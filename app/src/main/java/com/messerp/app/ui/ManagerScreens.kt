package com.messerp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.messerp.app.data.ManagerRow
import com.messerp.app.data.Role

private fun typeLabel(t: String) = if (t == "MEAL") "মিল ম্যানেজার" else "বিদ্যুৎ ও রাঁধুনি বিল ম্যানেজার"

/** Main Admin only: give or take back manager duties and notice permissions. */
@Composable
fun ManagersScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    if (p.role != Role.MAIN_ADMIN) return
    var user by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("MEAL") }
    var general by rememberSaveable { mutableStateOf(false) }
    var meeting by rememberSaveable { mutableStateOf(false) }
    var saveDialog by rememberSaveable { mutableStateOf(false) }
    var revoke by remember { mutableStateOf<ManagerRow?>(null) }

    LaunchedEffect(Unit) { vm.loadManagers() }

    AppScaffold(vm, 0, "ম্যানেজার ও নোটিশের অনুমতি", "মূল অ্যাডমিন") {
        MessageText(vm)
        AppCard {
            Text("কীভাবে কাজ করে", fontWeight = FontWeight.Bold)
            Text(
                "মিল ম্যানেজার: বাজার খরচ, গেস্ট মিল ও সবার মিলের সারসংক্ষেপ দেখেন। " +
                    "বিদ্যুৎ ও রাঁধুনি বিল ম্যানেজার: এই দুই বিল সংগ্রহ, পেমেন্ট, বকেয়া ও রসিদ সামলান (মেসের মূল খাতা ছুঁতে পারেন না)। " +
                    "দুজনেই নিজের বিভাগের নোটিশ দিতে পারেন। সাধারণ নোটিশ ও জরুরি মিটিং শুধু আপনি অনুমতি দিলে পারবেন। " +
                    "ম্যানেজাররা সাধারণ সদস্যই থাকেন, তাই নিজের নামে চার্জ বা পেমেন্ট এন্ট্রি করতে পারেন না।",
                fontSize = 13.sp
            )
        }

        SectionTitle("বর্তমান ম্যানেজার")
        if (vm.managerRows.isEmpty()) Text("এখনো কাউকে দায়িত্ব দেওয়া হয়নি।", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.managerRows.forEach { r ->
            AppCard {
                Text("${r.name} (${r.code})", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(typeLabel(r.type), positive = r.active)
                    StatusChip(if (r.active) "সক্রিয়" else "বাতিল")
                }
                Text(
                    "সাধারণ নোটিশ: " + (if (r.canGeneralNotice) "অনুমতি আছে" else "নেই") +
                        " · জরুরি মিটিং: " + (if (r.canMeetingNotice) "অনুমতি আছে" else "নেই"),
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        user = r.userId
                        type = r.type
                        general = r.canGeneralNotice
                        meeting = r.canMeetingNotice
                    }) { Text("অনুমতি বদলান") }
                    if (r.active) OutlinedButton(onClick = { revoke = r }, enabled = !vm.busy) { Text("দায়িত্ব বাতিল") }
                }
            }
        }

        SectionTitle("নতুন নিয়োগ বা অনুমতি হালনাগাদ")
        AppCard {
            Text("সদস্য", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            vm.members.filter { it.role == Role.MEMBER && it.isActive }.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                    row.forEach { m -> ChoiceChip(m.fullName, user == m.id) { user = m.id } }
                }
            }
            Text("দায়িত্ব", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("মিল ম্যানেজার", type == "MEAL") { type = "MEAL" }
                ChoiceChip("বিদ্যুৎ ও রাঁধুনি", type == "BILL") { type = "BILL" }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = general, onCheckedChange = { general = it })
                Text("সাধারণ নোটিশ পাঠাতে পারবেন")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = meeting, onCheckedChange = { meeting = it })
                Text("জরুরি মিটিং নোটিফিকেশন পাঠাতে পারবেন")
            }
            PrimaryButton("সংরক্ষণ", !vm.busy && user.isNotEmpty()) { saveDialog = true }
            Text("প্রতিটি দায়িত্বে সর্বোচ্চ ৩ জন সক্রিয় থাকতে পারেন। প্রতিটি বদল অডিট লগে যায়।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (saveDialog) {
        ReasonDialog(
            "ম্যানেজার নিয়োগ",
            { reason -> vm.saveManager(user, type, true, general, meeting, reason); saveDialog = false },
            { saveDialog = false }
        )
    }
    revoke?.let { r ->
        ReasonDialog(
            "দায়িত্ব বাতিল",
            { reason -> vm.saveManager(r.userId, r.type, false, false, false, reason); revoke = null },
            { revoke = null }
        )
    }
}
