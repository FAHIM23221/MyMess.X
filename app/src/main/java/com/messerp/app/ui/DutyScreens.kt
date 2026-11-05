package com.messerp.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import com.messerp.app.data.Dates
import com.messerp.app.data.Duty
import com.messerp.app.data.Role

fun dutyLabel(t: String) = when (t) {
    "MARKET" -> "বাজার"
    "TOILET" -> "টয়লেট পরিষ্কার"
    "KITCHEN" -> "রান্নাঘর"
    else -> "অন্যান্য"
}

private fun dutyStatus(s: String) = when (s) {
    "COMPLETED" -> "সম্পন্ন"
    "CANNOT_COMPLETE" -> "পারবেন না"
    "REASSIGNED" -> "অন্যজনকে দেওয়া হয়েছে"
    "CANCELLED" -> "বাতিল"
    else -> "অপেক্ষমাণ"
}

private fun actionLabel(a: String) = when (a) {
    "ASSIGNED" -> "ডিউটি দেওয়া হয়েছে"
    "ASSIGNED_FROM_REASSIGN" -> "পুনঃবরাদ্দে পেয়েছেন"
    "COMPLETED" -> "সম্পন্ন হয়েছে"
    "CANNOT_DO" -> "\"পারবো না\" জানিয়েছেন"
    "REASSIGNED" -> "অন্যজনকে দেওয়া হয়েছে"
    "CANCELLED" -> "বাতিল হয়েছে"
    else -> a
}

@Composable
private fun NoteDialog(title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("নোট (ঐচ্ছিক)") }) },
        confirmButton = { TextButton(onClick = { onConfirm(note) }) { Text("নিশ্চিত করুন") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
private fun HistoryList(vm: AppViewModel, dutyId: String) {
    if (vm.historyFor != dutyId) return
    AppCard {
        Text("ইতিহাস", fontWeight = FontWeight.Bold)
        if (vm.history.isEmpty()) Text("লোড হচ্ছে...", fontSize = 12.sp)
        vm.history.forEach { h ->
            Text(
                actionLabel(h.action) + " · " + com.messerp.app.data.ReceiptPdf.formatLocal(h.atLocal) +
                    (if (h.actor != null) " · " + h.actor else "") + (if (!h.note.isNullOrBlank()) " · " + h.note else ""),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ManagerCard(vm: AppViewModel) {
    val rot = vm.rotation ?: return
    val current = rot.rows.filter { it.phase == "CURRENT" }
    val next = rot.rows.firstOrNull { it.phase == "UPCOMING" }
    AppCard(modifier = Modifier.clickable { vm.openRotation() }) {
        Text("মেসের ম্যানেজার", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (current.isEmpty()) {
            Text("এখন কোনো ম্যানেজার নির্ধারিত নেই", fontWeight = FontWeight.SemiBold)
        } else {
            current.forEach { c -> Text(c.name + " (" + Dates.bnDate(c.end) + " পর্যন্ত)", fontWeight = FontWeight.Bold) }
        }
        if (next != null) Text("পরবর্তী: " + next.name + " (" + Dates.bnDate(next.start) + " থেকে)", fontSize = 12.sp)
    }
}

@Composable
fun DutyScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    val isMember = p.role == Role.MEMBER
    val today = vm.rotation?.today ?: Dates.todayDhaka()
    var type by rememberSaveable(vm.formVersion) { mutableStateOf("MARKET") }
    var member by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var date by rememberSaveable(vm.formVersion) { mutableStateOf(today) }
    var notes by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var completeFor by remember { mutableStateOf<Duty?>(null) }
    var cannotFor by remember { mutableStateOf<Duty?>(null) }
    var cancelFor by remember { mutableStateOf<Duty?>(null) }
    var reassignFor by remember { mutableStateOf<Duty?>(null) }
    var reassignTo by rememberSaveable { mutableStateOf("") }
    var reassignReason by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadDuties() }

    AppScaffold(vm, 0, "ডিউটি", if (isMember) "আমার কাজ" else "অ্যাডমিন") {
        MessageText(vm)

        if (isMember) {
            SectionTitle("আমার ডিউটি")
            if (vm.myDuties.isEmpty()) Text("এখন কোনো ডিউটি নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.myDuties.forEach { d ->
                AppCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(dutyStatus(d.status), positive = d.status == "COMPLETED")
                        Text(Dates.bnDate(d.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(dutyLabel(d.type), fontWeight = FontWeight.Bold)
                    if (!d.notes.isNullOrBlank()) Text(d.notes, fontSize = 13.sp)
                    if (d.reassigned) Text("এটি পুনঃবরাদ্দে পাওয়া", fontSize = 12.sp)
                    if (d.status == "CANNOT_COMPLETE") Text("আপনার কারণ: " + (d.cannotReason ?: "—"), fontSize = 12.sp)
                    if (d.status == "PENDING") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { completeFor = d }, enabled = !vm.busy) { Text("সম্পন্ন করেছি") }
                            OutlinedButton(onClick = { cannotFor = d }, enabled = !vm.busy) { Text("আজ পারবো না") }
                        }
                    }
                    OutlinedButton(onClick = { vm.showHistory(d.id) }) { Text("ইতিহাস") }
                }
                HistoryList(vm, d.id)
            }
        } else {
            SectionTitle("নতুন ডিউটি দিন")
            AppCard {
                Text("সদস্য", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                vm.members.filter { it.role == Role.MEMBER && it.isActive }.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                        row.forEach { m -> ChoiceChip(m.fullName, member == m.id) { member = m.id } }
                    }
                }
                Text("ধরন", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                listOf("MARKET", "TOILET", "KITCHEN", "OTHER").chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                        row.forEach { t -> ChoiceChip(dutyLabel(t), type == t) { type = t } }
                    }
                }
                DateField("তারিখ", date) { date = it }
                Field("নোট (ঐচ্ছিক)", notes, { notes = it })
                PrimaryButton("ডিউটি দিন", !vm.busy) { vm.assignDuty(member, type, date, notes) }
            }

            SectionTitle("ডিউটির তালিকা (গত ৭ দিন থেকে আগামী ৩ সপ্তাহ)")
            if (vm.adminDuties.isEmpty()) Text("কোনো ডিউটি নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.adminDuties.forEach { d ->
                AppCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(dutyStatus(d.status), positive = d.status == "COMPLETED")
                        Text(Dates.bnDate(d.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(dutyLabel(d.type) + ": " + d.memberName, fontWeight = FontWeight.Bold)
                    if (!d.notes.isNullOrBlank()) Text(d.notes, fontSize = 13.sp)
                    if (d.status == "CANNOT_COMPLETE") Text("কারণ: " + (d.cannotReason ?: "—"), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    if (!d.completionNote.isNullOrBlank()) Text("নোট: " + d.completionNote, fontSize = 12.sp)
                    if (d.status == "PENDING" || d.status == "CANNOT_COMPLETE") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { reassignTo = ""; reassignFor = d }, enabled = !vm.busy) { Text("অন্যজনকে দিন") }
                            OutlinedButton(onClick = { cancelFor = d }, enabled = !vm.busy) { Text("বাতিল") }
                        }
                    }
                    if (d.status == "PENDING") {
                        OutlinedButton(onClick = { completeFor = d }, enabled = !vm.busy) { Text("সম্পন্ন চিহ্নিত করুন") }
                    }
                    OutlinedButton(onClick = { vm.showHistory(d.id) }) { Text("ইতিহাস") }
                }
                HistoryList(vm, d.id)
            }
        }

        SectionTitle("সবার ডিউটি রোস্টার (গত ৩ দিন থেকে আগামী ৭ দিন)")
        if (vm.roster.isEmpty()) Text("রোস্টারে কিছু নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.roster.forEach { d ->
            val mine = d.memberId == p.id
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    Dates.bnDate(d.date) + " · " + dutyLabel(d.type) + " · " + d.memberName,
                    fontSize = 13.sp,
                    fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(dutyStatus(d.status), positive = d.status == "COMPLETED")
            }
        }
        Text(
            "রোস্টারে \"পারবো না\"-র কারণ দেখানো হয় না, তা শুধু ওই সদস্য ও অ্যাডমিন দেখেন।",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    completeFor?.let { d ->
        NoteDialog("সম্পন্ন: " + dutyLabel(d.type), { note -> vm.completeDuty(d.id, note); completeFor = null }, { completeFor = null })
    }
    cannotFor?.let { d ->
        ReasonDialog("\"পারবো না\": " + dutyLabel(d.type), { reason -> vm.cannotDoDuty(d.id, reason); cannotFor = null }, { cannotFor = null })
    }
    cancelFor?.let { d ->
        ReasonDialog("ডিউটি বাতিল", { reason -> vm.cancelDuty(d.id, reason); cancelFor = null }, { cancelFor = null })
    }
    reassignFor?.let { d ->
        AlertDialog(
            onDismissRequest = { reassignFor = null },
            title = { Text("অন্যজনকে দিন: " + dutyLabel(d.type)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.members.filter { it.role == Role.MEMBER && it.isActive && it.id != d.memberId }.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { m -> ChoiceChip(m.fullName, reassignTo == m.id) { reassignTo = m.id } }
                        }
                    }
                    OutlinedTextField(value = reassignReason, onValueChange = { reassignReason = it }, label = { Text("কারণ (কমপক্ষে ৩ অক্ষর)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (reassignTo.isNotEmpty()) vm.reassignDuty(d.id, reassignTo, reassignReason)
                    reassignFor = null
                }) { Text("নিশ্চিত করুন") }
            },
            dismissButton = { TextButton(onClick = { reassignFor = null }) { Text("বাতিল") } }
        )
    }
}

@Composable
fun RotationScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    val isMain = p.role == Role.MAIN_ADMIN
    val rot = vm.rotation
    val today = rot?.today ?: Dates.todayDhaka()
    var member by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var start by rememberSaveable(vm.formVersion) { mutableStateOf(today) }
    var end by rememberSaveable(vm.formVersion) { mutableStateOf(Dates.addDays(today, 29)) }
    var note by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var overlap by rememberSaveable(vm.formVersion) { mutableStateOf(false) }
    var cancelFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.loadRotation() }

    AppScaffold(vm, 0, "ম্যানেজার রোটেশন", "সময় ঢাকা অনুযায়ী, সার্ভার থেকে") {
        MessageText(vm)
        if (rot == null) {
            Text("লোড হচ্ছে...")
        } else {
            val current = rot.rows.filter { it.phase == "CURRENT" }
            AppCard {
                Text("বর্তমান ম্যানেজার", fontWeight = FontWeight.Bold)
                if (current.isEmpty()) Text("এখন কেউ নির্ধারিত নেই।")
                current.forEach { c -> Text(c.name + " (" + Dates.bnDate(c.start) + " – " + Dates.bnDate(c.end) + ")") }
            }
            SectionTitle("আগামী সূচি")
            val upcoming = rot.rows.filter { it.phase == "UPCOMING" }
            if (upcoming.isEmpty()) Text("কোনো আগামী নিয়োগ নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
            upcoming.forEach { r ->
                AppCard {
                    Text(r.name + " (" + r.code + ")", fontWeight = FontWeight.Bold)
                    Text(Dates.bnDate(r.start) + " থেকে " + Dates.bnDate(r.end), fontSize = 13.sp)
                    if (r.allowOverlap) Text("একসঙ্গে থাকার অনুমতি দেওয়া", fontSize = 12.sp)
                    if (!r.note.isNullOrBlank()) Text(r.note, fontSize = 12.sp)
                    if (isMain) OutlinedButton(onClick = { cancelFor = r.id }, enabled = !vm.busy) { Text("নিয়োগ বাতিল") }
                }
            }
            val past = rot.rows.filter { it.phase == "PAST" }
            if (past.isNotEmpty()) {
                SectionTitle("সাম্প্রতিক অতীত")
                past.forEach { r -> Text(r.name + ": " + Dates.bnDate(r.start) + " – " + Dates.bnDate(r.end), fontSize = 13.sp) }
            }
            if (current.isNotEmpty() && isMain) {
                SectionTitle("চলমান নিয়োগ বাতিল")
                current.forEach { c ->
                    OutlinedButton(onClick = { cancelFor = c.id }, enabled = !vm.busy) { Text(c.name + " এর নিয়োগ বাতিল") }
                }
            }
        }

        if (isMain) {
            SectionTitle("নতুন নিয়োগ")
            AppCard {
                Text("সদস্য", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                vm.members.filter { it.role == Role.MEMBER && it.isActive }.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                        row.forEach { m -> ChoiceChip(m.fullName, member == m.id) { member = m.id } }
                    }
                }
                DateField("শুরুর তারিখ", start) { start = it }
                DateField("শেষ তারিখ (এই দিন পর্যন্ত)", end) { end = it }
                Field("নোট (ঐচ্ছিক)", note, { note = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = overlap, onCheckedChange = { overlap = it })
                    Text("অন্য ম্যানেজারের সঙ্গে একসঙ্গে থাকতে পারবেন")
                }
                PrimaryButton("নিয়োগ সংরক্ষণ", !vm.busy) { vm.saveRotation(member, start, end, note, overlap) }
                Text(
                    "একই সময়ে দুজন ম্যানেজার নিয়োগ করতে গেলে সার্ভার আটকাবে, শুধু উপরের টিক দিলে চলবে। শুরুর তারিখ অতীতের হলে চলবে, কিন্তু পুরো সময়কাল অতীতের হলে চলবে না।",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    cancelFor?.let { id ->
        ReasonDialog("নিয়োগ বাতিল", { reason -> vm.cancelRotation(id, reason); cancelFor = null }, { cancelFor = null })
    }
}
