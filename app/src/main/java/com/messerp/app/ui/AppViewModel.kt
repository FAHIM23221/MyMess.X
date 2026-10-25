package com.messerp.app.ui

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messerp.app.config.AppConfig
import com.messerp.app.data.AdminRepository
import com.messerp.app.data.AvatarRepository
import com.messerp.app.data.ImageUtil
import com.messerp.app.data.AuthException
import com.messerp.app.data.AuthRepository
import com.messerp.app.data.DuesOverview
import com.messerp.app.data.Expense
import com.messerp.app.data.GuestMeal
import com.messerp.app.data.LedgerRepository
import com.messerp.app.data.MealRepository
import com.messerp.app.data.MealStatus
import com.messerp.app.data.MealSummary
import com.messerp.app.data.MemberLedger
import com.messerp.app.data.PaymentRow
import com.messerp.app.data.ReceiptPdf
import com.messerp.app.data.Money
import com.messerp.app.data.Dates
import com.messerp.app.data.Profile
import com.messerp.app.data.Role
import com.messerp.app.data.SessionStore
import com.messerp.app.data.Validators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    data object Bills : Screen
    data object Finance : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = AuthRepository(SessionStore(app))
    private val admin = AdminRepository(auth)
    private val meals = MealRepository(auth)
    private val ledgerRepo = LedgerRepository(auth)
    private val avatars = AvatarRepository(auth)

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

    var ledger by mutableStateOf<MemberLedger?>(null)
        private set
    var myLedger by mutableStateOf<MemberLedger?>(null)
        private set
    var ledgerMonth by mutableStateOf("")
        private set
    var ledgerMemberId by mutableStateOf<String?>(null)
        private set
    var dues by mutableStateOf<DuesOverview?>(null)
        private set
    var payments by mutableStateOf<List<PaymentRow>>(emptyList())
        private set
    var lastPaymentId by mutableStateOf<String?>(null)
        private set
    var lastReceiptNo by mutableStateOf<String?>(null)
        private set

    val avatarCache = mutableStateMapOf<String, ImageBitmap>()
    private val avatarLoading = mutableSetOf<String>()

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
        if (p.role == Role.MEMBER) try {
            myLedger = ledgerRepo.ledger(null, null)
        } catch (e: Exception) {
            // The balance card stays empty; the Bills screen shows the real error if it persists.
        }
        if (p.role != Role.MEMBER) {
            try {
                dues = ledgerRepo.dues()
            } catch (e: Exception) {
                // Shown as a dash on the dashboard.
            }
            loadAdminData(p)
        }
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

    fun register(
        name: String, phoneRaw: String,
        fatherName: String, fatherPhoneRaw: String, motherName: String, motherPhoneRaw: String,
        room: String, seat: String,
        password: String, confirm: String, inviteRaw: String
    ) {
        if (!configured) return error("ব্যাকএন্ড কনফিগার করা নেই।")
        val phone = Validators.normalizePhone(phoneRaw)
        val fp = if (fatherPhoneRaw.isBlank()) "" else Validators.normalizePhone(fatherPhoneRaw)
        val mp = if (motherPhoneRaw.isBlank()) "" else Validators.normalizePhone(motherPhoneRaw)
        val invite = Validators.normalizeInvite(inviteRaw)
        val fatherGiven = fatherName.isNotBlank() || fp.isNotEmpty()
        val motherGiven = motherName.isNotBlank() || mp.isNotEmpty()
        when {
            !Validators.isValidName(name) -> return error("নাম ২ থেকে ৬০ অক্ষরের হতে হবে।")
            !Validators.isValidPhone(phone) -> return error("সঠিক ফোন নম্বর দিন (01XXXXXXXXX)।")
            fatherGiven && (!Validators.isValidName(fatherName) || !Validators.isValidPhone(fp)) ->
                return error("বাবার নাম ও সঠিক ফোন নম্বর (01XXXXXXXXX) দিন।")
            motherGiven && (!Validators.isValidName(motherName) || !Validators.isValidPhone(mp)) ->
                return error("মায়ের নাম ও সঠিক ফোন নম্বর (01XXXXXXXXX) দিন।")
            room.isBlank() || seat.isBlank() -> return error("রুম নম্বর ও সিট নম্বর দিন।")
            room.trim().length > 20 || seat.trim().length > 20 -> return error("রুম বা সিট নম্বর অনেক বড়।")
            !Validators.isValidPassword(password) -> return error("পাসওয়ার্ড কমপক্ষে ৮ অক্ষরের হতে হবে।")
            password != confirm -> return error("দুই পাসওয়ার্ড মেলেনি।")
            invite.isEmpty() -> return error("ইনভাইট কোড দিন।")
        }
        val extra = mapOf(
            "room_no" to room, "seat_no" to seat,
            "father_name" to fatherName, "father_phone" to fp,
            "mother_name" to motherName, "mother_phone" to mp
        )
        launchSafe { enter(auth.signUp(name, phone, password, invite, extra, fatherGiven || motherGiven)) }
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
            ledger = null
            myLedger = null
            dues = null
            payments = emptyList()
            lastPaymentId = null
            lastReceiptNo = null
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

    fun createInvite(role: Role, waiveGuardian: Boolean = false) {
        launchSafe {
            lastInvite = admin.createInvite(role, waiveGuardian)
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
        // Admins have no personal meal account; they only see the mess-wide summary.
        if (profile?.role == Role.MEMBER) mealStatus = meals.myStatus()
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

    private class Idem {
        private var key: String? = null
        private var id: String = UUID.randomUUID().toString()
        fun idFor(k: String): String {
            if (k != key) {
                key = k
                id = UUID.randomUUID().toString()
            }
            return id
        }
        fun clear() {
            key = null
        }
    }

    private val payIdem = Idem()
    private val chargeIdem = Idem()
    private val splitIdem = Idem()

    private suspend fun refreshLedger() {
        if (profile?.role != Role.MEMBER && ledgerMemberId == null) {
            ledger = null
            return
        }
        val l = ledgerRepo.ledger(ledgerMemberId, ledgerMonth.ifEmpty { null })
        ledger = l
        if (ledgerMonth.isEmpty()) ledgerMonth = l.monthStart
    }

    private suspend fun refreshMyLedger() {
        if (profile?.role != Role.MEMBER) return
        myLedger = ledgerRepo.ledger(null, null)
    }

    private suspend fun refreshFinance() {
        dues = ledgerRepo.dues()
        payments = ledgerRepo.listPayments()
        refreshLedger()
        refreshMyLedger()
        if (members.isEmpty()) members = admin.listMembers()
    }

    fun openBills() {
        ledgerMemberId = null
        ledgerMonth = ""
        ledger = null
        go(Screen.Bills)
    }

    fun openFinance() {
        ledgerMemberId = null
        ledgerMonth = ""
        ledger = null
        go(Screen.Finance)
    }

    fun loadLedger() {
        launchSafe {
            refreshLedger()
            refreshMyLedger()
        }
    }

    fun loadFinance() {
        launchSafe { refreshFinance() }
    }

    fun shiftMonth(delta: Int) {
        if (ledgerMonth.isEmpty()) return
        ledgerMonth = Dates.addMonths(ledgerMonth, delta)
        loadLedger()
    }

    fun selectLedgerMember(id: String?) {
        ledgerMemberId = id
        ledgerMonth = ""
        loadLedger()
    }

    fun recordPayment(memberId: String, amountText: String, method: String, date: String, txnRef: String, memo: String) {
        if (memberId.isEmpty()) return error("কার পেমেন্ট তা বাছুন।")
        val minor = Money.parseTakaToMinor(amountText)
            ?: return error("সঠিক পরিমাণ দিন (১ থেকে ১০,০০,০০০ টাকা, সর্বোচ্চ ২ দশমিক ঘর)।")
        val reqId = payIdem.idFor("$memberId|$minor|$method|$date|${txnRef.trim()}|${memo.trim()}")
        launchSafe {
            val result = ledgerRepo.recordPayment(memberId, minor, method, date, memo, txnRef, reqId)
            payIdem.clear()
            lastPaymentId = result.first
            lastReceiptNo = result.second
            refreshFinance()
            formVersion++
            note("পেমেন্ট সংরক্ষিত হয়েছে। রসিদ নম্বর: ${result.second}")
        }
    }

    fun reversePayment(id: String, reason: String) {
        launchSafe {
            ledgerRepo.reversePayment(id, reason)
            refreshFinance()
            note("পেমেন্ট বাতিল হয়েছে। রসিদে বাতিল চিহ্ন থাকবে।")
        }
    }

    fun addCharge(memberId: String, monthDate: String, category: String, amountText: String, desc: String) {
        if (memberId.isEmpty()) return error("কোন সদস্যকে চার্জ দেবেন তা বাছুন।")
        val minor = Money.parseTakaToMinor(amountText)
            ?: return error("সঠিক পরিমাণ দিন (১ থেকে ১০,০০,০০০ টাকা, সর্বোচ্চ ২ দশমিক ঘর)।")
        val month = Dates.monthStart(monthDate)
        val reqId = chargeIdem.idFor("$memberId|$month|$category|$minor|${desc.trim()}")
        launchSafe {
            ledgerRepo.addCharge(memberId, month, category, minor, desc, reqId)
            chargeIdem.clear()
            refreshFinance()
            formVersion++
            note("চার্জ যোগ হয়েছে।")
        }
    }

    fun splitCharge(monthDate: String, category: String, amountText: String, desc: String) {
        val minor = Money.parseTakaToMinor(amountText)
            ?: return error("সঠিক মোট পরিমাণ দিন (১ থেকে ১০,০০,০০০ টাকা, সর্বোচ্চ ২ দশমিক ঘর)।")
        val month = Dates.monthStart(monthDate)
        val reqId = splitIdem.idFor("$month|$category|$minor|${desc.trim()}")
        launchSafe {
            val n = ledgerRepo.splitCharge(month, category, minor, desc, reqId)
            splitIdem.clear()
            refreshFinance()
            formVersion++
            note("${Money.bn(n.toLong())} জন সদস্যের মধ্যে সমান ভাগ হয়েছে।")
        }
    }

    fun postMeal(monthDate: String, force: Boolean, reason: String) {
        val month = Dates.monthStart(monthDate)
        launchSafe {
            val n = ledgerRepo.postMealCharges(month, force, reason)
            refreshFinance()
            note(if (n > 0) "${Money.bn(n.toLong())} জন সদস্যের মিল চার্জ বসেছে।" else "নতুন কোনো চার্জ বসেনি (আগেই বসেছে বা মিল নেই)।")
        }
    }

    fun reverseCharge(entryId: String, reason: String) {
        launchSafe {
            ledgerRepo.reverseCharge(entryId, reason)
            refreshFinance()
            note("চার্জ বাতিল হয়েছে।")
        }
    }

    fun downloadReceipt(paymentId: String) {
        launchSafe {
            val r = ledgerRepo.receipt(paymentId)
            val name = "Receipt-${r.receiptNo}.pdf"
            val bytes = ReceiptPdf.build(r)
            val saved = withContext(Dispatchers.IO) { ReceiptPdf.saveToDownloads(getApplication<Application>(), name, bytes) }
            if (saved) note("রসিদ Downloads ফোল্ডারে সংরক্ষিত হয়েছে: $name")
            else error("এই Android সংস্করণে সরাসরি সংরক্ষণ সম্ভব নয়। শেয়ার বাটন ব্যবহার করুন।")
        }
    }

    fun shareReceipt(paymentId: String) {
        launchSafe {
            val r = ledgerRepo.receipt(paymentId)
            val name = "Receipt-${r.receiptNo}.pdf"
            val bytes = ReceiptPdf.build(r)
            val uri = withContext(Dispatchers.IO) { ReceiptPdf.cacheUri(getApplication<Application>(), name, bytes) }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "রসিদ শেয়ার করুন").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            getApplication<Application>().startActivity(chooser)
        }
    }

    fun saveProfile(name: String, fatherName: String, fatherPhoneRaw: String, motherName: String, motherPhoneRaw: String) {
        val fp = if (fatherPhoneRaw.isBlank()) "" else Validators.normalizePhone(fatherPhoneRaw)
        val mp = if (motherPhoneRaw.isBlank()) "" else Validators.normalizePhone(motherPhoneRaw)
        val fatherGiven = fatherName.isNotBlank() || fp.isNotEmpty()
        val motherGiven = motherName.isNotBlank() || mp.isNotEmpty()
        if (!Validators.isValidName(name)) return error("নাম ২ থেকে ৬০ অক্ষরের হতে হবে।")
        if (fatherGiven && (!Validators.isValidName(fatherName) || !Validators.isValidPhone(fp))) {
            return error("বাবার নাম ও সঠিক ফোন নম্বর (01XXXXXXXXX) দিন।")
        }
        if (motherGiven && (!Validators.isValidName(motherName) || !Validators.isValidPhone(mp))) {
            return error("মায়ের নাম ও সঠিক ফোন নম্বর (01XXXXXXXXX) দিন।")
        }
        launchSafe {
            profile = auth.updateProfile(name, fatherName, fp, motherName, mp)
            note("তথ্য সংরক্ষিত হয়েছে।")
        }
    }

    fun uploadAvatar(uri: Uri) {
        val p = profile ?: return
        launchSafe {
            val bytes = withContext(Dispatchers.IO) { ImageUtil.prepareJpeg(getApplication<Application>(), uri) }
                ?: throw AuthException("ছবি পড়া যায়নি। অন্য ছবি বেছে নিন।")
            val path = avatars.upload(p.id, bytes)
            profile = p.copy(avatarUrl = path)
            note("প্রোফাইল ছবি আপডেট হয়েছে।")
        }
    }

    fun loadAvatar(path: String) {
        if (avatarCache.containsKey(path) || !avatarLoading.add(path)) return
        viewModelScope.launch {
            try {
                val bytes = avatars.fetch(path)
                if (bytes != null) {
                    val bmp = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                    if (bmp != null) avatarCache[path] = bmp
                }
            } catch (e: Exception) {
                // The initial letter is shown instead of the photo.
            } finally {
                avatarLoading.remove(path)
            }
        }
    }
}
