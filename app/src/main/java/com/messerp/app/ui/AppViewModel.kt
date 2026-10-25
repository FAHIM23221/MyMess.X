package com.messerp.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messerp.app.config.AppConfig
import com.messerp.app.data.AdminRepository
import com.messerp.app.data.AuthException
import com.messerp.app.data.AuthRepository
import com.messerp.app.data.Expense
import com.messerp.app.data.GuestMeal
import com.messerp.app.data.MealRepository
import com.messerp.app.data.MealStatus
import com.messerp.app.data.MealSummary
import com.messerp.app.data.Money
import com.messerp.app.data.Dates
import com.messerp.app.data.Profile
import com.messerp.app.data.Role
import com.messerp.app.data.SessionStore
import com.messerp.app.data.Validators
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

sealed interface Screen {
    data object Splash : Screen
    data object Login : Screen
    data object Register : Screen
    data object AdminLogin : Screen
    data object Home : Screen
    data object ProfileScreen : Screen
    data object Meal : Screen
    data object Expenses : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = AuthRepository(SessionStore(app))
    private val admin = AdminRepository(auth)
    private val meals = MealRepository(auth)

    var screen by mutableStateOf<Screen>(Screen.Splash)
        private set
    var profile by mutableStateOf<Profile?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var messageIsError by mutableStateOf(false)
        private set
    var members by mutableStateOf<List<Profile>>(emptyList())
        private set
    var leaderboard by mutableStateOf(false)
        private set
    var lastInvite by mutableStateOf<String?>(null)
        private set

    var mealStatus by mutableStateOf<MealStatus?>(null)
        private set
    var summary by mutableStateOf<MealSummary?>(null)
        private set
    var expenses by mutableStateOf<List<Expense>>(emptyList())
        private set
    var guestMeals by mutableStateOf<List<GuestMeal>>(emptyList())
        private set
    var formVersion by mutableIntStateOf(0)
        private set

    val configured: Boolean get() = AppConfig.isSupabaseConfigured

    init {
        restore()
    }

    fun go(target: Screen) {
        message = null
        screen = target
    }

    private fun error(text: String) {
        message = text
        messageIsError = true
    }

    private fun note(text: String) {
        message = text
        messageIsError = false
    }

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            message = null
            try {
                block()
            } catch (e: AuthException) {
                error(e.userMessage)
            } catch (e: IOException) {
                error("ইন্টারনেট সংযোগ যাচাই করে আবার চেষ্টা করুন।")
            } catch (e: Exception) {
                error("অপ্রত্যাশিত সমস্যা হয়েছে। আবার চেষ্টা করুন।")
            } finally {
                busy = false
            }
        }
    }

    fun comingSoon() {
        note("এই সুবিধা পরের ধাপে আসছে।")
    }

    fun restore() {
        if (!configured) {
            screen = Screen.Login
            return
        }
        launchSafe {
            try {
                val p = auth.restore()
                if (p == null) screen = Screen.Login else enter(p)
            } catch (e: AuthException) {
                screen = Screen.Login
                throw e
            }
        }
    }

    private suspend fun enter(p: Profile) {
        profile = p
        screen = Screen.Home
        try {
            refreshMeal()
        } catch (e: Exception) {
            error("মিলের তথ্য আনা যায়নি।")
        }
        if (p.role != Role.MEMBER) loadAdminData(p)
    }

    private suspend fun loadAdminData(p: Profile) {
        members = admin.listMembers()
        if (p.role == Role.MAIN_ADMIN) leaderboard = admin.leaderboardEnabled()
    }

    fun login(phoneRaw: String, password: String, requireAdmin: Boolean) {
        if (!configured) return error("ব্যাকএন্ড কনফিগার করা নেই।")
        val phone = Validators.normalizePhone(phoneRaw)
        if (!Validators.isValidPhone(phone) || password.isEmpty()) {
            return error("সঠিক ফোন নম্বর (01XXXXXXXXX) ও পাসওয়ার্ড দিন।")
        }
        launchSafe {
            val p = auth.signIn(phone, password)
            if (requireAdmin && p.role == Role.MEMBER) {
                auth.signOut()
                throw AuthException("এই অ্যাকাউন্টে অ্যাডমিন অনুমতি নেই।")
            }
            enter(p)
        }
    }

    fun register(name: String, phoneRaw: String, password: String, confirm: String, inviteRaw: String) {
        if (!configured) return error("ব্যাকএন্ড কনফিগার করা নেই।")
        val phone = Validators.normalizePhone(phoneRaw)
        val invite = Validators.normalizeInvite(inviteRaw)
        when {
            !Validators.isValidName(name) -> return error("নাম ২ থেকে ৬০ অক্ষরের হতে হবে।")
            !Validators.isValidPhone(phone) -> return error("সঠিক ফোন নম্বর দিন (01XXXXXXXXX)।")
            !Validators.isValidPassword(password) -> return error("পাসওয়ার্ড কমপক্ষে ৮ অক্ষরের হতে হবে।")
            password != confirm -> return error("দুই পাসওয়ার্ড মেলেনি।")
            invite.isEmpty() -> return error("ইনভাইট কোড দিন।")
        }
        launchSafe { enter(auth.signUp(name, phone, password, invite)) }
    }

    fun logout() {
        viewModelScope.launch {
            auth.signOut()
            profile = null
            members = emptyList()
            lastInvite = null
            leaderboard = false
            mealStatus = null
            summary = null
            expenses = emptyList()
            guestMeals = emptyList()
            go(Screen.Login)
        }
    }

    fun refreshAdmin() {
        val p = profile ?: return
        if (p.role == Role.MEMBER) return
        launchSafe { loadAdminData(p) }
    }

    fun saveName(name: String) {
        if (!Validators.isValidName(name)) return error("নাম ২ থেকে ৬০ অক্ষরের হতে হবে।")
        launchSafe {
            profile = auth.updateName(name)
            note("নাম সংরক্ষিত হয়েছে।")
        }
    }

    fun createInvite(role: Role) {
        launchSafe {
            lastInvite = admin.createInvite(role)
            note("কোড তৈরি হয়েছে। এটি ৭ দিন বৈধ এবং একবারই ব্যবহার করা যাবে।")
        }
    }

    fun changeRole(target: Profile, role: Role, reason: String) {
        launchSafe {
            admin.setRole(target.id, role, reason.trim())
            profile?.let { loadAdminData(it) }
            note("রোল বদলানো হয়েছে।")
        }
    }

    fun changeStatus(target: Profile, status: String, reason: String) {
        launchSafe {
            admin.setStatus(target.id, status, reason.trim())
            profile?.let { loadAdminData(it) }
            note("অবস্থা বদলানো হয়েছে।")
        }
    }

    fun toggleLeaderboard(enabled: Boolean) {
        launchSafe {
            admin.setLeaderboard(enabled)
            leaderboard = enabled
            note(if (enabled) "লিডারবোর্ড চালু হয়েছে।" else "লিডারবোর্ড বন্ধ হয়েছে।")
        }
    }

    private suspend fun refreshMeal() {
        mealStatus = meals.myStatus()
        summary = meals.summary()
    }

    fun loadMeal() {
        launchSafe { refreshMeal() }
    }

    private suspend fun refreshExpenses() {
        refreshMeal()
        val start = summary?.monthStart ?: return
        expenses = meals.listExpenses(start, Dates.endOfMonth(start))
        guestMeals = meals.listGuestMeals(start)
        val p = profile
        if (p != null && p.role != Role.MEMBER && members.isEmpty()) members = admin.listMembers()
    }

    fun loadExpenses() {
        launchSafe { refreshExpenses() }
    }

    fun createOff(from: String, to: String, b: Boolean, l: Boolean, d: Boolean, reason: String) {
        if (to < from) return error("শেষ তারিখ শুরুর তারিখের আগে হতে পারে না।")
        if (!b && !l && !d) return error("অন্তত একটি বেলা বাছুন।")
        launchSafe {
            meals.createOff(from, to, b, l, d, reason)
            refreshMeal()
            formVersion++
            note("অনুরোধ সংরক্ষিত হয়েছে। শেষ তারিখের পর মিল নিজে চালু হবে।")
        }
    }

    fun cancelOff(id: String) {
        launchSafe {
            meals.cancelOff(id)
            refreshMeal()
            note("হালনাগাদ করা হয়েছে।")
        }
    }

    // A retry of the same data reuses the same request id, so the server never records it twice.
    private var expenseKey: String? = null
    private var expenseReqId: String = UUID.randomUUID().toString()
    private var guestKey: String? = null
    private var guestReqId: String = UUID.randomUUID().toString()

    fun addExpense(date: String, category: String, amountText: String, description: String) {
        val minor = Money.parseTakaToMinor(amountText)
            ?: return error("সঠিক পরিমাণ দিন (১ থেকে ১০,০০,০০০ টাকা, সর্বোচ্চ ২ দশমিক ঘর)।")
        val desc = description.trim()
        if (desc.isEmpty()) return error("বিবরণ লিখুন।")
        val key = "$date|$category|$minor|$desc"
        if (key != expenseKey) {
            expenseKey = key
            expenseReqId = UUID.randomUUID().toString()
        }
        val reqId = expenseReqId
        launchSafe {
            meals.addExpense(date, category, minor, desc, reqId)
            expenseKey = null
            refreshExpenses()
            formVersion++
            note("খরচ সংরক্ষিত হয়েছে।")
        }
    }

    fun voidExpense(id: String, reason: String) {
        launchSafe {
            meals.voidExpense(id, reason)
            refreshExpenses()
            note("খরচ বাতিল হয়েছে।")
        }
    }

    fun addGuest(memberId: String, date: String, slot: String, countText: String) {
        if (memberId.isEmpty()) return error("কার গেস্ট তা বাছুন।")
        val count = countText.trim().toIntOrNull()
        if (count == null || count < 1 || count > 20) return error("গেস্ট সংখ্যা ১ থেকে ২০ হতে হবে।")
        val key = "$memberId|$date|$slot|$count"
        if (key != guestKey) {
            guestKey = key
            guestReqId = UUID.randomUUID().toString()
        }
        val reqId = guestReqId
        launchSafe {
            meals.addGuestMeal(memberId, date, slot, count, reqId)
            guestKey = null
            refreshExpenses()
            formVersion++
            note("গেস্ট মিল সংরক্ষিত হয়েছে।")
        }
    }

    fun voidGuest(id: String, reason: String) {
        launchSafe {
            meals.voidGuestMeal(id, reason)
            refreshExpenses()
            note("গেস্ট মিল বাতিল হয়েছে।")
        }
    }
}
