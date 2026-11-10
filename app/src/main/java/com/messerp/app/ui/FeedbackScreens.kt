package com.messerp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messerp.app.data.AdminFeedback
import com.messerp.app.data.Dates
import com.messerp.app.data.Money
import com.messerp.app.data.Role

private fun kindLabel(k: String) = if (k == "COMPLAINT") "অভিযোগ" else "পরামর্শ"

private fun topicLabel(t: String) = when (t) {
    "FOOD" -> "খাবার"
    "CLEANLINESS" -> "পরিষ্কার-পরিচ্ছন্নতা"
    "BILLS" -> "বিল ও হিসাব"
    "BEHAVIOR" -> "আচরণ"
    else -> "অন্যান্য"
}

private fun fbStatus(s: String) = when (s) {
    "SEEN" -> "দেখা হয়েছে"
    "RESOLVED" -> "সমাধান হয়েছে"
    "REJECTED" -> "গ্রহণ হয়নি"
    "REMOVED" -> "সরানো হয়েছে"
    else -> "নতুন"
}

@Composable
fun FeedbackScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    LaunchedEffect(Unit) { vm.loadFeedback() }
    if (p.role == Role.MEMBER) MemberFeedback(vm) else AdminFeedbackView(vm)
}

@Composable
private fun MemberFeedback(vm: AppViewModel) {
    var kind by rememberSaveable(vm.formVersion) { mutableStateOf("SUGGESTION") }
    var topic by rememberSaveable(vm.formVersion) { mutableStateOf("FOOD") }
    var body by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    AppScaffold(vm, 0, "পরামর্শ ও অভিযোগ", "নামহীন") {
        MessageText(vm)
        AppCard {
            Text("আপনার পরিচয় গোপন থাকে", fontWeight = FontWeight.Bold)
            Text(
                "লেখার সঙ্গে আপনার নাম বা কোনো পরিচয় ডাটাবেসে রাখা হয় না, অ্যাডমিনও জানতে পারেন না। শুধু তারিখ থাকে, সময় নয়। দিনে সর্বোচ্চ ৩টি লেখা যায়। অন্যকে আঘাত করার ভাষা এড়ান, অশোভন লেখা অ্যাডমিন সরিয়ে দিতে পারেন।",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("ধরন", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SUGGESTION", "COMPLAINT").forEach { k -> ChoiceChip(kindLabel(k), kind == k) { kind = k } }
        }
        Text("বিষয়", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        listOf("FOOD", "CLEANLINESS", "BILLS", "BEHAVIOR", "OTHER").chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                row.forEach { t -> ChoiceChip(topicLabel(t), topic == t) { topic = t } }
            }
        }
        Field("আপনার লেখা (১০ থেকে ১০০০ অক্ষর)", body, { body = it }, singleLine = false)
        Text(Money.bn(body.trim().length.toString()) + " / ১০০০", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton("নামহীনভাবে পাঠান", !vm.busy && body.trim().length >= 10) { vm.submitFeedback(kind, topic, body) }

        SectionTitle("আমার জমা দেওয়া লেখা")
        if (vm.myFeedback.isEmpty()) Text("এখনো কিছু জমা দেননি।", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.myFeedback.forEach { f ->
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(kindLabel(f.kind))
                    StatusChip(topicLabel(f.topic))
                    StatusChip(fbStatus(f.status), positive = f.status == "RESOLVED")
                }
                Text(Dates.bnDate(f.createdDay), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (f.body != null) Text(f.body, fontSize = 14.sp)
                if (!f.response.isNullOrBlank()) {
                    Text("অ্যাডমিনের উত্তর:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(f.response, fontSize = 13.sp)
                }
            }
        }
        Text(
            "উত্তর দেখতে এই ফোনের গোপন চাবি লাগে। ফোন বদলালে বা লগআউট করলে আগের লেখার উত্তর আর দেখা যাবে না।",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdminFeedbackView(vm: AppViewModel) {
    var respondFor by remember { mutableStateOf<AdminFeedback?>(null) }
    var hideFor by remember { mutableStateOf<String?>(null) }
    var newStatus by rememberSaveable { mutableStateOf("SEEN") }
    var answer by rememberSaveable { mutableStateOf("") }
    AppScaffold(vm, 0, "পরামর্শ ও অভিযোগ", "অ্যাডমিন, লেখকের পরিচয় অজানা") {
        MessageText(vm)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("সব", vm.feedbackFilter == null) { vm.filterFeedback(null) }
            ChoiceChip("নতুন", vm.feedbackFilter == "NEW") { vm.filterFeedback("NEW") }
            ChoiceChip("সমাধান", vm.feedbackFilter == "RESOLVED") { vm.filterFeedback("RESOLVED") }
        }
        if (vm.adminFeedback.isEmpty()) Text("কোনো লেখা নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.adminFeedback.forEach { f ->
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(kindLabel(f.kind))
                    StatusChip(topicLabel(f.topic))
                    StatusChip(fbStatus(f.status), positive = f.status == "RESOLVED")
                }
                Text(Dates.bnDate(f.createdDay), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(f.body, fontSize = 14.sp)
                if (!f.response.isNullOrBlank()) Text("আপনাদের উত্তর: " + f.response, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { newStatus = if (f.status == "NEW") "SEEN" else f.status; answer = f.response ?: ""; respondFor = f }, enabled = !vm.busy) {
                        Text("অবস্থা ও উত্তর")
                    }
                    OutlinedButton(onClick = { hideFor = f.id }, enabled = !vm.busy) { Text("সরিয়ে দিন") }
                }
            }
        }
        Text(
            "লেখকের পরিচয় কোথাও সংরক্ষিত নেই। অশোভন লেখা সরালে কারণ অডিট লগে থাকে, লেখাটি নয়।",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    hideFor?.let { id -> ReasonDialog("লেখা সরান", { reason -> vm.hideFeedback(id, reason); hideFor = null }, { hideFor = null }) }
    respondFor?.let { f ->
        AlertDialog(
            onDismissRequest = { respondFor = null },
            title = { Text("অবস্থা ও উত্তর") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SEEN", "RESOLVED", "REJECTED").forEach { s -> ChoiceChip(fbStatus(s), newStatus == s) { newStatus = s } }
                    }
                    OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("উত্তর (গ্রহণ না হলে বাধ্যতামূলক)") })
                    Text("উত্তরটি শুধু লেখক দেখতে পাবেন।", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { vm.respondFeedback(f.id, newStatus, answer); respondFor = null }) { Text("সংরক্ষণ") } },
            dismissButton = { TextButton(onClick = { respondFor = null }) { Text("বাতিল") } }
        )
    }
}
