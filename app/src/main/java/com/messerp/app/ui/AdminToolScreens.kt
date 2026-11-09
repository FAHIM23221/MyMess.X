package com.messerp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messerp.app.data.Dates
import com.messerp.app.data.Money
import com.messerp.app.data.Profile
import com.messerp.app.data.ReceiptPdf
import java.security.SecureRandom

/** Characters that are easy to read aloud and type (no 0/O, 1/l/I). */
fun generateTempPassword(): String {
    val alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789"
    val rnd = SecureRandom()
    return (1..10).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
}

fun auditLabel(a: String) = when (a) {
    "PASSWORD_RESET" -> "পাসওয়ার্ড রিসেট"
    "PAYMENT_RECORD" -> "পেমেন্ট গ্রহণ"
    "PAYMENT_REVERSE" -> "পেমেন্ট বাতিল"
    "CHARGE_ADD" -> "চার্জ যোগ"
    "CHARGE_SPLIT" -> "চার্জ ভাগ"
    "CHARGE_REVERSE" -> "চার্জ বাতিল"
    "EXPENSE_ADD" -> "খরচ যোগ"
    "EXPENSE_VOID" -> "খরচ বাতিল"
    "GUEST_MEAL_ADD" -> "গেস্ট মিল যোগ"
    "GUEST_MEAL_VOID" -> "গেস্ট মিল বাতিল"
    "NOTICE_CREATE" -> "নোটিশ প্রকাশ"
    "MANAGER_ROLE_SET" -> "ম্যানেজার নিয়োগ/বাতিল"
    "ROTATION_SET" -> "ম্যানেজার রোটেশন"
    "ROTATION_CANCEL" -> "রোটেশন বাতিল"
    "DUTY_ASSIGN" -> "ডিউটি দেওয়া"
    "DUTY_REASSIGN" -> "ডিউটি পুনঃবরাদ্দ"
    "DUTY_CANCEL" -> "ডিউটি বাতিল"
    "DUE_POLICY_SET" -> "বকেয়ার নীতি"
    "DUE_THRESHOLD" -> "বকেয়ার সীমা পার"
    "MEAL_SUSPEND" -> "মিল স্থগিত"
    "MEAL_RESUME" -> "মিল চালু"
    "MEAL_SUSPEND_OVERRIDE" -> "মিল স্থগিতে ছাড়"
    "RICE_RECORD" -> "চাল জমা"
    "RICE_ADJUST" -> "চাল সংশোধন"
    "RICE_REVERSE" -> "চাল বাতিল"
    "RICE_POLICY_SET" -> "চালের নীতি"
    "RICE_REQUIREMENT_SET" -> "চালের পরিমাণ নির্ধারণ"
    "FINE_ADD" -> "জরিমানা"
    "FINE_PAY" -> "জরিমানা পরিশোধ"
    "FINE_VOID" -> "জরিমানা বাতিল"
    "MESSAGE_DELETE_MOD" -> "বার্তা মুছেছেন (মডারেশন)"
    "FEEDBACK_RESPOND" -> "পরামর্শ/অভিযোগের উত্তর"
    "FEEDBACK_HIDE" -> "পরামর্শ/অভিযোগ সরানো"
    else -> a
}

@Composable
private fun ChangePasswordForm(vm: AppViewModel, forced: Boolean) {
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var again by rememberSaveable { mutableStateOf("") }
    Field(if (forced) "অ্যাডমিনের দেওয়া অস্থায়ী পাসওয়ার্ড" else "বর্তমান পাসওয়ার্ড", current, { current = it }, password = true)
    Field("নতুন পাসওয়ার্ড (কমপক্ষে ৮ অক্ষর)", next, { next = it }, password = true)
    Field("নতুন পাসওয়ার্ড আবার লিখুন", again, { again = it }, password = true)
    MessageText(vm)
    PrimaryButton("পাসওয়ার্ড বদলান", !vm.busy) { vm.changePassword(current, next, again) }
}

@Composable
fun ChangePasswordScreen(vm: AppViewModel, forced: Boolean) {
    if (forced) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("নতুন পাসওয়ার্ড দিন", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "অ্যাডমিন আপনার পাসওয়ার্ড রিসেট করেছেন। অস্থায়ী পাসওয়ার্ডটি অ্যাডমিনও জানেন, তাই এখনই নিজের গোপন পাসওয়ার্ড বানান।",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChangePasswordForm(vm, true)
            OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("লগআউট") }
        }
    } else {
        AppScaffold(vm, 4, "পাসওয়ার্ড বদল", "নিরাপত্তা") {
            ChangePasswordForm(vm, false)
            Text(
                "বদলানোর আগে বর্তমান পাসওয়ার্ড যাচাই করা হয়। পাসওয়ার্ড কেউ দেখতে পায় না, অ্যাডমিনও না।",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ResetPasswordDialog(vm: AppViewModel, target: Profile, onDismiss: () -> Unit) {
    var password by rememberSaveable { mutableStateOf(generateTempPassword()) }
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("পাসওয়ার্ড রিসেট: " + target.fullName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "নিচের অস্থায়ী পাসওয়ার্ডটি সদস্যকে নিজে জানান। এটি আর দেখা যাবে না। সদস্য প্রথম লগইনেই নতুন পাসওয়ার্ড বানাতে বাধ্য হবেন। তাঁর সব চালু লগইন বন্ধ হয়ে যাবে।",
                    fontSize = 12.sp
                )
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("অস্থায়ী পাসওয়ার্ড") })
                TextButton(onClick = { password = generateTempPassword() }) { Text("নতুন এলোমেলো পাসওয়ার্ড") }
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("কারণ (অডিট লগে যাবে)") })
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.resetMemberPassword(target.id, password, reason); onDismiss() }) { Text("রিসেট করুন") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
fun EditMemberDialog(vm: AppViewModel, target: Profile, onDismiss: () -> Unit) {
    var room by rememberSaveable { mutableStateOf(target.roomNo ?: "") }
    var seat by rememberSaveable { mutableStateOf(target.seatNo ?: "") }
    var join by rememberSaveable { mutableStateOf(target.joinDate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("সদস্যের তথ্য: " + target.fullName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("রুম নম্বর") })
                OutlinedTextField(value = seat, onValueChange = { seat = it }, label = { Text("সিট নম্বর") })
                DateField("যোগদানের তারিখ", join) { join = it }
                Text("যোগদানের তারিখ বদলালে মিল ও ভাড়ার হিসাব ওই তারিখ থেকে ধরা হয়।", fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { vm.updateMemberInfo(target.id, room, seat, join); onDismiss() }) { Text("সংরক্ষণ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
fun AuditScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadAudit(reset = true) }
    AppScaffold(vm, 0, "অডিট লগ", "শুধু মূল অ্যাডমিন") {
        MessageText(vm)
        Text("কে, কখন, কী করেছেন তার স্থায়ী রেকর্ড। এখান থেকে কিছু মোছা বা বদলানো যায় না।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("সব", vm.auditFilter == null) { vm.setAuditFilter(null) }
        }
        vm.auditActions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                row.forEach { a ->
                    ChoiceChip(auditLabel(a.action) + " (" + Money.bn(a.count.toString()) + ")", vm.auditFilter == a.action) { vm.setAuditFilter(a.action) }
                }
            }
        }
        if (vm.auditRows.isEmpty()) Text("কোনো রেকর্ড নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.auditRows.forEach { r ->
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(auditLabel(r.action))
                    Text(ReceiptPdf.formatLocal(r.atLocal), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("করেছেন: " + (r.actor ?: "সিস্টেম"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (r.targetName != null) Text("কার ক্ষেত্রে: " + r.targetName, fontSize = 13.sp)
                if (!r.reason.isNullOrBlank()) Text("কারণ: " + r.reason, fontSize = 12.sp)
                if (!r.newValue.isNullOrBlank()) Text("পরে: " + r.newValue, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!r.oldValue.isNullOrBlank()) Text("আগে: " + r.oldValue, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (vm.auditHasMore) {
            OutlinedButton(onClick = { vm.loadAudit(reset = false) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("আরও পুরোনো রেকর্ড") }
        }
    }
}
