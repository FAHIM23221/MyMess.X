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
import com.messerp.app.data.AppNotification
import com.messerp.app.data.ManagerRepository
import com.messerp.app.data.ManagerRole
import com.messerp.app.data.ManagerRow
import com.messerp.app.data.NoticeRow
import com.messerp.app.data.NotificationRepository
import com.messerp.app.data.Notifier
import com.messerp.app.data.NotifyScheduler
import com.messerp.app.data.Push
import com.messerp.app.data.PushRepository
import com.messerp.app.data.AvatarRepository
import com.messerp.app.data.CashBook
import com.messerp.app.data.ChatMessage
import com.messerp.app.data.ChatRead
import com.messerp.app.data.ChatRepository
import com.messerp.app.data.ChatScheduler
import com.messerp.app.data.ChatStore
import com.messerp.app.data.ChatSync
import com.messerp.app.data.Conversation
import com.messerp.app.data.OutboxItem
import com.messerp.app.data.DueBoard
import com.messerp.app.data.DueRepository
import com.messerp.app.data.DueStatus
import com.messerp.app.data.Duty
import com.messerp.app.data.DutyEvent
import com.messerp.app.data.DutyRepository
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
import com.messerp.app.data.Fine
import com.messerp.app.data.MemberRice
import com.messerp.app.data.PaymentRow
import com.messerp.app.data.RiceFineRepository
import com.messerp.app.data.RiceSummary
import com.messerp.app.data.Weight
import com.messerp.app.data.Rotation
import com.messerp.app.data.ReceiptPdf
import com.messerp.app.data.Transparency
import com.messerp.app.data.TransparencyRepository
import com.messerp.app.data.Money
import com.messerp.app.data.Dates
import com.messerp.app.data.Profile
import com.messerp.app.data.Role
import com.messerp.app.data.SessionStore
import com.messerp.app.data.Validators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    data object Notifications : Screen
    data object Managers : Screen
    data object TransparencyPage : Screen
    data object CashBookPage : Screen
    data object DuePage : Screen
    data object DutyPage : Screen
    data object RotationPage : Screen
    data object RicePage : Screen
    data object FinesPage : Screen
    data object ChatListPage : Screen
    data object ChatPage : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = AuthRepository(SessionStore(app))
    private val admin = AdminRepository(auth)
    private val meals = MealRepository(auth)
    private val ledgerRepo = LedgerRepository(auth)
    private val avatars = AvatarRepository(auth)
    private val notifs = NotificationRepository(auth)
    private val pushRepo = PushRepository(auth)
    private val mgrRepo = ManagerRepository(auth)
    private val tpRepo = TransparencyRepository(auth)
    private val dueRepo = DueRepository(auth)
    private val dutyRepo = DutyRepository(auth)
    private val dutyIdem = Idem()
    private val riceRepo = RiceFineRepository(auth)
    private val chatRepo = ChatRepository(auth)
    private val chatStore = ChatStore(app)
    private var chatJob: Job? = null
    private var chatSince: Long? = null
    private var lastTypingSent = 0L
    private var serverMsgs = LinkedHashMap<String, ChatMessage>()
    private val riceIdem = Idem()
    private val adjustIdem = Idem()
    private val fineIdem = Idem()
    private val payFineIdem = Idem()

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

    var notifications by mutableStateOf<List<AppNotification>>(emptyList())
        private set
    var noticeHistory by mutableStateOf<List<NoticeRow>>(emptyList())
        private set
    val unreadCount: Int get() = notifications.count { !it.read }
    private var pollJob: Job? = null
    private var avatarErrorShown = false
    var managerRoles by mutableStateOf<List<ManagerRole>>(emptyList())
        private set
    var managerRows by mutableStateOf<List<ManagerRow>>(emptyList())
        private set
    var activeBook by mutableStateOf("MESS")
        private set
    var transparency by mutableStateOf<Transparency?>(null)
        private set
    var cashBook by mutableStateOf<CashBook?>(null)
        private set
    var tpBook by mutableStateOf("MESS")
        private set
    var dueStatus by mutableStateOf<DueStatus?>(null)
        private set
    var myDuties by mutableStateOf<List<Duty>>(emptyList())
        private set
    var adminDuties by mutableStateOf<List<Duty>>(emptyList())
        private set
    var roster by mutableStateOf<List<Duty>>(emptyList())
        private set
    var rotation by mutableStateOf<Rotation?>(null)
        private set
    var riceSummary by mutableStateOf<RiceSummary?>(null)
        private set
    var memberRice by mutableStateOf<MemberRice?>(null)
        private set
    var riceMember by mutableStateOf<String?>(null)
        private set
    var riceMonth by mutableStateOf("")
        private set
    var myFines by mutableStateOf<List<Fine>>(emptyList())
        private set
    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set
    var currentConv by mutableStateOf<Conversation?>(null)
        private set
    var chatMessages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var chatReads by mutableStateOf<List<ChatRead>>(emptyList())
        private set
    var chatTyping by mutableStateOf<List<String>>(emptyList())
        private set
    var chatOffline by mutableStateOf(false)
        private set
    val chatUnread: Int get() = conversations.sumOf { it.unread }
    var adminFines by mutableStateOf<List<Fine>>(emptyList())
        private set
    var historyFor by mutableStateOf<String?>(null)
        private set
    var history by mutableStateOf<List<DutyEvent>>(emptyList())
        private set
    val isManagerNow: Boolean get() = rotation?.rows?.any { it.phase == "CURRENT" && it.memberId == profile?.id } == true
    var dueBoard by mutableStateOf<DueBoard?>(null)
        private set
    var tpMonth by mutableStateOf("")
        private set
    var myUtilLedger by mutableStateOf<MemberLedger?>(null)
        private set
    val isMealMgr: Boolean get() = managerRoles.any { it.type == "MEAL" }
    val isBillMgr: Boolean get() = managerRoles.any { it.type == "BILL" }
    val canGeneralNotice: Boolean get() = managerRoles.any { it.canGeneralNotice }
    val canMeetingNotice: Boolean get() = managerRoles.any { it.canMeetingNotice }
    val canPublish: Boolean get() = (profile?.let { it.role != Role.MEMBER } == true) || managerRoles.isNotEmpty()
    var popupQueue by mutableStateOf<List<AppNotification>>(emptyList())
        private set
    var headlineEnabled by mutableStateOf(false)
        private set
    var headlineText by mutableStateOf("")
        private set

    val avatarCache = mutableStateMapOf<String, ImageBitmap>()
    private val avatarLoading = mutableSetOf<String>()

    val configured: Boolean get() = AppConfig.isSupabaseConfigured

    init {
        restore()
    }

    fun go(target: Screen) {
        if (target != Screen.ChatPage) stopChatLoop()
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
        startNotifications()
        conversations = chatStore.loadConversations(p.id)
        viewModelScope.launch {
            try {
                ChatSync.flush(chatRepo, chatStore, p.id)
                conversations = chatRepo.conversations()
                chatStore.saveConversations(p.id, conversations)
            } catch (e: Exception) {
                // Chat refreshes when the phone is online again.
            }
        }
        try {
            rotation = dutyRepo.rotation()
            if (p.role == Role.MEMBER) myDuties = dutyRepo.myDuties()
        } catch (e: Exception) {
            // The manager and duty cards appear once the server answers.
        }
        try {
            if (p.role == Role.MEMBER) dueStatus = dueRepo.myStatus() else dueBoard = dueRepo.board()
        } catch (e: Exception) {
            // The due card appears once the server answers.
        }
        try {
            managerRoles = mgrRepo.myRoles()
        } catch (e: Exception) {
            // Manager tools stay hidden until the next refresh.
        }
        try {
            refreshMeal()
        } catch (e: Exception) {
            error("মিলের তথ্য আনা যায়নি।")
        }
        if (p.role == Role.MEMBER) try {
            myLedger = ledgerRepo.ledger(null, null, "MESS")
            myUtilLedger = ledgerRepo.ledger(null, null, "UTILITY")
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
            try {
                Push.tokenOrNull(getApplication<Application>())?.let { pushRepo.unregisterToken(it) }
            } catch (e: Exception) {
                // The server removes dead tokens by itself later.
            }
            val chatUid = profile?.id
            auth.signOut()
            if (chatUid != null) chatStore.clearUser(chatUid)
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
            stopNotifications()
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
        val l = ledgerRepo.ledger(ledgerMemberId, ledgerMonth.ifEmpty { null }, activeBook)
        ledger = l
        if (ledgerMonth.isEmpty()) ledgerMonth = l.monthStart
    }

    private suspend fun refreshMyLedger() {
        if (profile?.role != Role.MEMBER) return
        myLedger = ledgerRepo.ledger(null, null, "MESS")
        myUtilLedger = ledgerRepo.ledger(null, null, "UTILITY")
    }

    private suspend fun refreshFinance() {
        dues = ledgerRepo.dues(activeBook)
        payments = ledgerRepo.listPayments(activeBook)
        refreshLedger()
        refreshMyLedger()
        if (members.isEmpty() && profile?.role != Role.MEMBER) members = admin.listMembers()
    }

    fun openBills(book: String = "MESS") {
        activeBook = book

        ledgerMemberId = null
        ledgerMonth = ""
        ledger = null
        go(Screen.Bills)
    }

    fun openFinance(book: String = "MESS") {
        activeBook = book
        dues = null
        payments = emptyList()
        ledgerMemberId = null
        ledgerMonth = ""
        ledger = null
        go(Screen.Finance)
    }

    fun openManagers() {
        go(Screen.Managers)
    }

    fun switchBook(book: String) {
        activeBook = book
        ledgerMemberId = null
        ledgerMonth = ""
        ledger = null
        dues = null
        payments = emptyList()
        if (screen == Screen.Finance) loadFinance() else loadLedger()
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
        val reqId = payIdem.idFor("$memberId|$minor|$method|$date|${txnRef.trim()}|${memo.trim()}|$activeBook")
        launchSafe {
            val result = ledgerRepo.recordPayment(memberId, minor, method, date, memo, txnRef, reqId, activeBook)
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
        val reqId = chargeIdem.idFor("$memberId|$month|$category|$minor|${desc.trim()}|$activeBook")
        launchSafe {
            ledgerRepo.addCharge(memberId, month, category, minor, desc, reqId, activeBook)
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
        val reqId = splitIdem.idFor("$month|$category|$minor|${desc.trim()}|$activeBook")
        launchSafe {
            val n = ledgerRepo.splitCharge(month, category, minor, desc, reqId, activeBook)
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
                if (bytes == null && !avatarErrorShown) {
                    avatarErrorShown = true
                    error("প্রোফাইল ছবি লোড করা যায়নি।")
                }
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

    private val noticeIdem = Idem()

    private fun startNotifications() {
        val app = getApplication<Application>()
        NotifyScheduler.start(app)
        viewModelScope.launch {
            try {
                Push.tokenOrNull(app)?.let { pushRepo.registerToken(it) }
            } catch (e: Exception) {
                // Instant push is optional; polling still works.
            }
        }
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                try {
                    if (Notifier.canPost(app)) {
                        for (n in notifs.claim(20)) Notifier.show(app, n)
                    }
                    notifications = notifs.list(50)
                    popupQueue = notifs.popups()
                    managerRoles = mgrRepo.myRoles()
                    if (profile?.role == Role.MEMBER) {
                        dueStatus = dueRepo.myStatus()
                        myDuties = dutyRepo.myDuties()
                    }
                    rotation = dutyRepo.rotation()
                    if (screen != Screen.ChatPage) {
                        conversations = chatRepo.conversations()
                        profile?.let { chatStore.saveConversations(it.id, conversations) }
                    }
                    val h = notifs.headline()
                    headlineEnabled = h.first
                    headlineText = h.second
                } catch (e: Exception) {
                    // Offline or session issue: try again on the next round.
                }
                delay(30_000)
            }
        }
    }

    private fun stopNotifications() {
        pollJob?.cancel()
        pollJob = null
        NotifyScheduler.stop(getApplication<Application>())
        notifications = emptyList()
        noticeHistory = emptyList()
        popupQueue = emptyList()
        headlineEnabled = false
        headlineText = ""
        managerRoles = emptyList()
        managerRows = emptyList()
        myUtilLedger = null
        activeBook = "MESS"
        transparency = null
        cashBook = null
        tpBook = "MESS"
        tpMonth = ""
        dueStatus = null
        dueBoard = null
        myDuties = emptyList()
        adminDuties = emptyList()
        conversations = emptyList()
        currentConv = null
        chatMessages = emptyList()
        chatReads = emptyList()
        chatTyping = emptyList()
        riceSummary = null
        memberRice = null
        riceMember = null
        riceMonth = ""
        myFines = emptyList()
        adminFines = emptyList()
        roster = emptyList()
        rotation = null
        historyFor = null
        history = emptyList()
    }

    fun loadNotifications() {
        launchSafe {
            notifications = notifs.list(50)
            if (canPublish) noticeHistory = notifs.noticeHistory()
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            try {
                notifs.markRead(listOf(id))
                notifications = notifs.list(50)
            } catch (e: Exception) {
                // Ignored; it stays unread.
            }
        }
    }

    fun markAllRead() {
        launchSafe {
            notifs.markAllRead()
            notifications = notifs.list(50)
            note("সব বিজ্ঞপ্তি পড়া হিসেবে চিহ্নিত হয়েছে।")
        }
    }

    fun createNotice(title: String, body: String, level: String, audience: String, type: String, popup: Boolean, department: String) {
        val t = title.trim().ifEmpty { if (type == "EMERGENCY_MEETING") "জরুরি মিটিং" else "" }
        if (t.isEmpty() || body.isBlank()) return error("শিরোনাম ও বিবরণ লিখুন।")
        val reqId = noticeIdem.idFor("$t|${body.trim()}|$level|$audience|$type|$popup|$department")
        launchSafe {
            val result = notifs.createNotice(t, body.trim(), level, audience, type, popup, department, reqId)
            noticeIdem.clear()
            notifications = notifs.list(50)
            noticeHistory = notifs.noticeHistory()
            formVersion++
            val pushResult = try {
                pushRepo.sendNotice(result.first)
            } catch (e: Exception) {
                null
            }
            val tail = if (pushResult == null) {
                " তাৎক্ষণিক পুশ সেটআপ বাকি, তাই ফোনে সর্বোচ্চ ১৫ মিনিটে পৌঁছাবে।"
            } else {
                " পুশ গেছে " + Money.bn(pushResult.sent.toLong()) + " জনের ফোনে" +
                    (if (pushResult.noToken > 0) "; " + Money.bn(pushResult.noToken.toLong()) + " জনের ফোন এখনো নিবন্ধিত নয় (সর্বোচ্চ ১৫ মিনিটে পাবেন)" else "") + "।"
            }
            note("নোটিশ পাঠানো হয়েছে। প্রাপক: " + Money.bn(result.second.toLong()) + " জন।" + tail)
        }
    }

    fun dismissPopup(id: String) {
        popupQueue = popupQueue.filter { it.id != id }
        viewModelScope.launch {
            try {
                notifs.markPopupSeen(id)
                notifications = notifs.list(50)
            } catch (e: Exception) {
                // It may appear again at the next check.
            }
        }
    }

    fun saveHeadline(text: String, enabled: Boolean) {
        val t = text.trim()
        if (t.length > 200) return error("হেডলাইন সর্বোচ্চ ২০০ অক্ষরের হতে পারে।")
        if (enabled && t.isEmpty()) return error("হেডলাইন চালু করতে আগে লেখা দিন।")
        launchSafe {
            notifs.setHeadline(t, enabled)
            headlineText = t
            headlineEnabled = enabled
            note(if (enabled) "স্ক্রলিং হেডলাইন চালু হয়েছে।" else "স্ক্রলিং হেডলাইন বন্ধ হয়েছে।")
        }
    }

    fun sendDueReminders() {
        val book = activeBook
        launchSafe {
            val n = ledgerRepo.sendDueReminders(book)
            note(if (n > 0) "বকেয়ার স্মরণ গেছে " + Money.bn(n.toLong()) + " জন সদস্যের কাছে।" else "বকেয়া আছে এমন কোনো সদস্য নেই।")
        }
    }

    fun loadManagers() {
        launchSafe {
            managerRows = mgrRepo.list()
            if (members.isEmpty()) members = admin.listMembers()
        }
    }

    fun saveManager(userId: String, type: String, active: Boolean, general: Boolean, meeting: Boolean, reason: String) {
        launchSafe {
            mgrRepo.set(userId, type, active, general, meeting, reason)
            managerRows = mgrRepo.list()
            note(if (active) "ম্যানেজারের দায়িত্ব ও অনুমতি সংরক্ষিত হয়েছে।" else "ম্যানেজারের দায়িত্ব বাতিল হয়েছে।")
        }
    }

    fun openTransparency(book: String = "MESS") {
        tpBook = book
        tpMonth = ""
        transparency = null
        cashBook = null
        go(Screen.TransparencyPage)
    }

    fun openCashBook() {
        cashBook = null
        go(Screen.CashBookPage)
    }

    fun switchTpBook(book: String) {
        tpBook = book
        tpMonth = ""
        transparency = null
        cashBook = null
        if (screen == Screen.CashBookPage) loadCashBook() else loadTransparency()
    }

    fun shiftTpMonth(delta: Int) {
        if (tpMonth.isEmpty()) return
        tpMonth = Dates.addMonths(tpMonth, delta)
        if (screen == Screen.CashBookPage) loadCashBook() else loadTransparency()
    }

    fun loadTransparency() {
        launchSafe {
            val t = tpRepo.transparency(tpBook, tpMonth.ifEmpty { null })
            transparency = t
            if (tpMonth.isEmpty()) tpMonth = t.monthStart
        }
    }

    fun loadCashBook() {
        launchSafe {
            val c = tpRepo.cashBook(tpBook, tpMonth.ifEmpty { null })
            cashBook = c
            if (tpMonth.isEmpty()) tpMonth = c.monthStart
        }
    }

    fun openDue() {
        go(Screen.DuePage)
    }

    fun loadDue() {
        launchSafe {
            if (profile?.role == Role.MEMBER) dueStatus = dueRepo.myStatus() else dueBoard = dueRepo.board()
        }
    }

    fun saveDuePolicy(warningText: String, criticalText: String, suspend: Boolean, notify: Boolean, reason: String) {
        fun minor(t: String): Long? = if (t.isBlank() || t.trim() == "0") 0L else Money.parseTakaToMinor(t)
        val w = minor(warningText) ?: return error("সতর্কতার সীমা সঠিক নয়।")
        val c = minor(criticalText) ?: return error("জরুরি সীমা সঠিক নয়।")
        launchSafe {
            dueRepo.setPolicy(w, c, suspend, notify, reason)
            dueBoard = dueRepo.board()
            note("নীতি সংরক্ষিত হয়েছে।")
        }
    }

    fun overrideSuspension(memberId: String, days: Int, reason: String) {
        launchSafe {
            dueRepo.override(memberId, days, reason)
            dueBoard = dueRepo.board()
            note("সাময়িক ছাড় দেওয়া হয়েছে।")
        }
    }

    fun openDuties() {
        historyFor = null
        history = emptyList()
        go(Screen.DutyPage)
    }

    fun loadDuties() {
        launchSafe {
            roster = dutyRepo.roster()
            if (profile?.role == Role.MEMBER) {
                myDuties = dutyRepo.myDuties()
            } else {
                adminDuties = dutyRepo.adminDuties()
                if (members.isEmpty()) members = admin.listMembers()
            }
        }
    }

    private suspend fun refreshDuties() {
        roster = dutyRepo.roster()
        if (profile?.role == Role.MEMBER) myDuties = dutyRepo.myDuties() else adminDuties = dutyRepo.adminDuties()
    }

    fun showHistory(id: String) {
        if (historyFor == id) {
            historyFor = null
            history = emptyList()
            return
        }
        historyFor = id
        history = emptyList()
        launchSafe { history = dutyRepo.history(id) }
    }

    fun assignDuty(memberId: String, type: String, date: String, notes: String) {
        if (memberId.isEmpty()) return error("কাকে ডিউটি দেবেন তা বাছুন।")
        val reqId = dutyIdem.idFor("$memberId|$type|$date|${notes.trim()}")
        launchSafe {
            dutyRepo.assign(memberId, type, date, notes, reqId)
            dutyIdem.clear()
            refreshDuties()
            formVersion++
            note("ডিউটি দেওয়া হয়েছে। সদস্য বিজ্ঞপ্তি পাবেন।")
        }
    }

    fun completeDuty(id: String, noteText: String) {
        launchSafe {
            dutyRepo.complete(id, noteText)
            refreshDuties()
            note("ডিউটি সম্পন্ন হিসেবে চিহ্নিত হয়েছে।")
        }
    }

    fun cannotDoDuty(id: String, reason: String) {
        launchSafe {
            dutyRepo.cannot(id, reason)
            refreshDuties()
            note("অ্যাডমিনকে জানানো হয়েছে।")
        }
    }

    fun reassignDuty(id: String, newMember: String, reason: String) {
        launchSafe {
            dutyRepo.reassign(id, newMember, reason)
            refreshDuties()
            note("ডিউটি অন্যজনকে দেওয়া হয়েছে।")
        }
    }

    fun cancelDuty(id: String, reason: String) {
        launchSafe {
            dutyRepo.cancel(id, reason)
            refreshDuties()
            note("ডিউটি বাতিল হয়েছে।")
        }
    }

    fun openRotation() {
        go(Screen.RotationPage)
    }

    fun loadRotation() {
        launchSafe {
            rotation = dutyRepo.rotation()
            if (profile?.role == Role.MAIN_ADMIN && members.isEmpty()) members = admin.listMembers()
        }
    }

    fun saveRotation(memberId: String, start: String, end: String, noteText: String, overlap: Boolean) {
        if (memberId.isEmpty()) return error("কাকে ম্যানেজার করবেন তা বাছুন।")
        if (end < start) return error("শেষ তারিখ শুরুর তারিখের আগে হতে পারে না।")
        launchSafe {
            dutyRepo.setRotation(memberId, start, end, noteText, overlap)
            rotation = dutyRepo.rotation()
            formVersion++
            note("ম্যানেজার নিয়োগ সংরক্ষিত হয়েছে। সদস্য বিজ্ঞপ্তি পাবেন।")
        }
    }

    fun cancelRotation(id: String, reason: String) {
        launchSafe {
            dutyRepo.cancelRotation(id, reason)
            rotation = dutyRepo.rotation()
            note("নিয়োগ বাতিল হয়েছে।")
        }
    }

    // ---------- Rice ----------
    fun openRice() {
        riceMember = null
        riceMonth = ""
        riceSummary = null
        memberRice = null
        go(Screen.RicePage)
    }

    private suspend fun refreshRice() {
        val s = riceRepo.riceSummary(riceMonth.ifEmpty { null })
        riceSummary = s
        if (riceMonth.isEmpty()) riceMonth = s.monthStart
        val target = riceMember ?: if (profile?.role == Role.MEMBER) profile?.id else null
        memberRice = if (target != null) riceRepo.memberRice(if (target == profile?.id) null else target, riceMonth) else null
    }

    fun loadRice() {
        launchSafe { refreshRice() }
    }

    fun shiftRiceMonth(delta: Int) {
        if (riceMonth.isEmpty()) return
        riceMonth = Dates.addMonths(riceMonth, delta)
        loadRice()
    }

    fun selectRiceMember(id: String) {
        riceMember = id
        loadRice()
    }

    fun recordRice(memberId: String, kgText: String, date: String, noteText: String) {
        if (memberId.isEmpty()) return error("কার চাল জমা নিচ্ছেন তা বাছুন।")
        val grams = Weight.parseKgToGrams(kgText) ?: return error("সঠিক পরিমাণ দিন (কেজিতে, সর্বোচ্চ ৩ দশমিক ঘর, ১০০০ কেজির মধ্যে)।")
        val reqId = riceIdem.idFor("$memberId|$grams|$date|${noteText.trim()}")
        launchSafe {
            riceRepo.record(memberId, grams, date, noteText, reqId)
            riceIdem.clear()
            refreshRice()
            formVersion++
            note("চাল জমা সংরক্ষিত হয়েছে।")
        }
    }

    fun adjustRice(memberId: String, kgText: String, reason: String) {
        if (memberId.isEmpty()) return error("সদস্য বাছুন।")
        val grams = Weight.parseKgToGrams(kgText, allowNegative = true) ?: return error("সঠিক সংশোধন দিন (কমাতে - চিহ্ন দিন)।")
        val month = riceMonth.ifEmpty { Dates.monthStart(Dates.todayDhaka()) }
        val reqId = adjustIdem.idFor("$memberId|$month|$grams|${reason.trim()}")
        launchSafe {
            riceRepo.adjust(memberId, month, grams, reason, reqId)
            adjustIdem.clear()
            refreshRice()
            formVersion++
            note("সংশোধন সংরক্ষিত হয়েছে।")
        }
    }

    fun reverseRice(id: String, reason: String) {
        launchSafe {
            riceRepo.reverse(id, reason)
            refreshRice()
            note("এন্ট্রি বাতিল হয়েছে।")
        }
    }

    fun saveRicePolicy(kgText: String, reason: String) {
        val grams = if (kgText.isBlank() || kgText.trim() == "0") 0L else Weight.parseKgToGrams(kgText)
            ?: return error("সঠিক পরিমাণ দিন।")
        launchSafe {
            riceRepo.setPolicy(grams, reason)
            refreshRice()
            note("নীতি সংরক্ষিত হয়েছে।")
        }
    }

    fun saveRiceRequirement(memberId: String, kgText: String, reason: String) {
        if (memberId.isEmpty()) return error("সদস্য বাছুন।")
        val grams = if (kgText.trim() == "0") 0L else Weight.parseKgToGrams(kgText) ?: return error("সঠিক পরিমাণ দিন।")
        val month = riceMonth.ifEmpty { Dates.monthStart(Dates.todayDhaka()) }
        launchSafe {
            riceRepo.setRequirement(memberId, month, grams, reason)
            refreshRice()
            note("নির্ধারিত পরিমাণ সংরক্ষিত হয়েছে।")
        }
    }

    // ---------- Fines ----------
    fun openFines() {
        go(Screen.FinesPage)
    }

    private suspend fun refreshFines() {
        if (profile?.role == Role.MEMBER) {
            myFines = riceRepo.myFines()
        } else {
            adminFines = riceRepo.adminFines()
            if (members.isEmpty()) members = admin.listMembers()
        }
    }

    fun loadFines() {
        launchSafe { refreshFines() }
    }

    fun addFine(memberId: String, amountText: String, reason: String, date: String) {
        if (memberId.isEmpty()) return error("কাকে জরিমানা করছেন তা বাছুন।")
        val minor = Money.parseTakaToMinor(amountText) ?: return error("সঠিক পরিমাণ দিন (১ থেকে ১০,০০,০০০ টাকা)।")
        if (reason.trim().length < 3) return error("জরিমানার কারণ লিখুন (কমপক্ষে ৩ অক্ষর)।")
        val reqId = fineIdem.idFor("$memberId|$minor|${reason.trim()}|$date")
        launchSafe {
            riceRepo.addFine(memberId, minor, reason, date, reqId)
            fineIdem.clear()
            refreshFines()
            formVersion++
            note("জরিমানা দেওয়া হয়েছে। সদস্য বিজ্ঞপ্তি পাবেন।")
        }
    }

    fun payFine(fineId: String, amountText: String, method: String, date: String, memo: String, txn: String) {
        val minor = Money.parseTakaToMinor(amountText) ?: return error("সঠিক পরিমাণ দিন।")
        val reqId = payFineIdem.idFor("$fineId|$minor|$method|$date|${txn.trim()}")
        launchSafe {
            val r = riceRepo.payFine(fineId, minor, method, date, memo, txn, reqId)
            payFineIdem.clear()
            lastPaymentId = r.first
            lastReceiptNo = r.second
            refreshFines()
            note("পরিশোধ সংরক্ষিত হয়েছে। রসিদ নম্বর: " + r.second)
        }
    }

    fun voidFine(id: String, reason: String) {
        launchSafe {
            riceRepo.voidFine(id, reason)
            refreshFines()
            note("জরিমানা বাতিল হয়েছে।")
        }
    }

    // ---------- Chat (text only; unsent messages wait on the phone) ----------
    private fun uid(): String = profile?.id ?: ""

    private fun rebuildChat() {
        val conv = currentConv ?: return
        val server = serverMsgs.values.sortedWith(compareBy({ it.ts }, { it.id }))
        val queued = chatStore.loadOutbox(uid()).filter { it.conversationId == conv.id }.sortedBy { it.createdMs }.map {
            ChatMessage(it.id, it.conversationId, uid(), profile?.fullName, it.body, Long.MAX_VALUE, Long.MAX_VALUE, Dates.nowDhakaLocal(), false, false, it.state)
        }
        chatMessages = server + queued.filter { q -> server.none { it.id == q.id } }
    }

    fun ensureMembersLoaded() {
        if (members.isNotEmpty() || profile?.role == Role.MEMBER) return
        viewModelScope.launch {
            try {
                members = admin.listMembers()
            } catch (e: Exception) {
                // The member picker stays empty until the next visit.
            }
        }
    }

    fun loadConversations() {
        viewModelScope.launch {
            try {
                conversations = chatRepo.conversations()
                chatStore.saveConversations(uid(), conversations)
                chatOffline = false
            } catch (e: Exception) {
                chatOffline = true
            }
        }
    }

    fun openChatList() {
        conversations = chatStore.loadConversations(uid())
        go(Screen.ChatListPage)
        loadConversations()
    }

    fun openChat(conv: Conversation) {
        currentConv = conv
        serverMsgs = LinkedHashMap()
        for (m in chatStore.loadMessages(uid(), conv.id)) serverMsgs[m.id] = m
        chatSince = null
        chatReads = emptyList()
        chatTyping = emptyList()
        rebuildChat()
        go(Screen.ChatPage)
        startChatLoop(conv)
    }

    fun startPrivateWith(memberId: String) {
        launchSafe {
            val id = chatRepo.openPrivate(memberId)
            conversations = chatRepo.conversations()
            chatStore.saveConversations(uid(), conversations)
            val c = conversations.firstOrNull { it.id == id } ?: throw AuthException("কথোপকথন পাওয়া যায়নি।")
            openChat(c)
        }
    }

    private fun stopChatLoop() {
        chatJob?.cancel()
        chatJob = null
        currentConv = null
    }

    private suspend fun flushOutboxInternal() {
        val sent = ChatSync.flush(chatRepo, chatStore, uid())
        val conv = currentConv
        for (m in sent) if (conv != null && m.conversationId == conv.id) serverMsgs[m.id] = m
        if (sent.isNotEmpty()) rebuildChat()
    }

    private fun startChatLoop(conv: Conversation) {
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            while (true) {
                try {
                    flushOutboxInternal()
                    val batch = chatRepo.messages(conv.id, chatSince, null, 50)
                    for (m in batch.messages) serverMsgs[m.id] = m
                    while (serverMsgs.size > 300) serverMsgs.remove(serverMsgs.keys.first())
                    chatStore.saveMessages(uid(), conv.id, serverMsgs.values.sortedBy { it.ts })
                    // A little overlap makes sure a message committed just before this check is not missed.
                    chatSince = batch.serverTs - 2_000_000L
                    chatReads = batch.reads
                    chatTyping = batch.typing
                    chatOffline = false
                    rebuildChat()
                    chatRepo.markRead(conv.id)
                } catch (e: Exception) {
                    chatOffline = true
                    rebuildChat()
                }
                delay(2500)
            }
        }
    }

    fun sendChat(text: String) {
        val conv = currentConv ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        if (body.length > 1000) return error("বার্তা সর্বোচ্চ ১০০০ অক্ষরের হতে পারে।")
        chatStore.addOutbox(uid(), OutboxItem(UUID.randomUUID().toString(), conv.id, body, System.currentTimeMillis(), "QUEUED"))
        rebuildChat()
        ChatScheduler.enqueue(getApplication<Application>())
        viewModelScope.launch {
            try {
                flushOutboxInternal()
            } catch (e: Exception) {
                chatOffline = true
            }
        }
    }

    fun retryChat(id: String) {
        chatStore.setOutboxState(uid(), id, "QUEUED")
        rebuildChat()
        ChatScheduler.enqueue(getApplication<Application>())
        viewModelScope.launch {
            try {
                flushOutboxInternal()
            } catch (e: Exception) {
                chatOffline = true
            }
        }
    }

    fun discardChat(id: String) {
        chatStore.removeOutbox(uid(), id)
        rebuildChat()
    }

    fun editChat(id: String, body: String) {
        val conv = currentConv ?: return
        launchSafe {
            val m = chatRepo.edit(id, body)
            serverMsgs[m.id] = m
            chatStore.saveMessages(uid(), conv.id, serverMsgs.values.sortedBy { it.ts })
            rebuildChat()
        }
    }

    fun deleteChat(id: String) {
        val conv = currentConv ?: return
        launchSafe {
            val m = chatRepo.delete(id)
            serverMsgs[m.id] = m
            chatStore.saveMessages(uid(), conv.id, serverMsgs.values.sortedBy { it.ts })
            rebuildChat()
        }
    }

    fun onChatTyping() {
        val conv = currentConv ?: return
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < 3000) return
        lastTypingSent = now
        viewModelScope.launch {
            try {
                chatRepo.typing(conv.id)
            } catch (e: Exception) {
                // Typing is only a hint.
            }
        }
    }
}
