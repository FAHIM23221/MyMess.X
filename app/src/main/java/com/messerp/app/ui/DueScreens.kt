package com.messerp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.messerp.app.data.Dates
import com.messerp.app.data.DueRow
import com.messerp.app.data.Money
import com.messerp.app.data.Role

fun dueLabel(status: String) = when (status) {
    "CRITICAL" -> "জরুরি"
    "WARNING" -> "সতর্কতা"
    else -> "পরিষ্কার"
}

private fun plainTaka(minor: Long): String =
    if (minor <= 0L) "" else if (minor % 100 == 0L) (minor / 100).toString() else String.format("%d.%02d", minor / 100, minor % 100)

@Composable
fun DueScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    LaunchedEffect(Unit) { vm.loadDue() }
    if (p.role == Role.MEMBER) MemberDueView(vm) else AdminDueView(vm, p.role == Role.MAIN_ADMIN)
}

@Composable
private fun MemberDueView(vm: AppViewModel) {
    val ds = vm.dueStatus
    AppScaffold(vm, 0, "বকেয়ার অবস্থা", "আমার তথ্য") {
        MessageText(vm)
        if (ds == null) {
            Text("লোড হচ্ছে...")
        } else {
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("বর্তমান অবস্থা", fontWeight = FontWeight.Bold)
                    StatusChip(dueLabel(ds.status), positive = ds.status == "CLEAR")
                }
                KeyValue("মোট বকেয়া", Money.format(ds.totalDueMinor))
                KeyValue("মেসের খাতা", Money.format(ds.messDueMinor))
                KeyValue("বিদ্যুৎ ও রাঁধুনি বিল", Money.format(ds.utilityDueMinor))
                KeyValue("চালের বাকি (এ মাস)", com.messerp.app.data.Weight.format(ds.riceDueGrams))
            }
            if (ds.suspended) {
                AppCard {
                    Text("মিল স্থগিত আছে", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("বকেয়া জরুরি সীমা পার হওয়ায় নীতি অনুযায়ী মিল স্থগিত। বকেয়া কমে সীমার নিচে এলে নিজে থেকে চালু হবে, অথবা অ্যাডমিন সাময়িক ছাড় দিতে পারেন।", fontSize = 13.sp)
                }
            }
            if (ds.overrideUntil != null) {
                Text("অ্যাডমিনের ছাড় আছে " + Dates.bnDate(ds.overrideUntil) + " পর্যন্ত।", fontSize = 13.sp)
            }
            AppCard {
                Text("সীমা", fontWeight = FontWeight.Bold)
                if (!ds.policy.configured) {
                    Text("এখনো কোনো সীমা ঠিক করা হয়নি।", fontSize = 13.sp)
                } else {
                    if (ds.policy.warningMinor > 0) KeyValue("সতর্কতা শুরু", Money.format(ds.policy.warningMinor))
                    if (ds.policy.criticalMinor > 0) KeyValue("জরুরি শুরু", Money.format(ds.policy.criticalMinor))
                    Text(
                        if (ds.policy.suspendEnabled) "জরুরি স্তরে মিল স্থগিতের নীতি চালু আছে।" else "মিল স্থগিতের নীতি বন্ধ আছে।",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "মোট বকেয়া = মেসের খাতার বাকি + বিদ্যুৎ ও রাঁধুনি খাতার বাকি। এক খাতার অগ্রিম অন্য খাতার বাকি কাটে না। আপনার অবস্থা শুধু আপনি ও অ্যাডমিন দেখতে পান।",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AdminDueView(vm: AppViewModel, isMain: Boolean) {
    val board = vm.dueBoard
    val pol = board?.policy
    var warnText by rememberSaveable(pol?.warningMinor, pol?.criticalMinor) { mutableStateOf(plainTaka(pol?.warningMinor ?: 0)) }
    var critText by rememberSaveable(pol?.warningMinor, pol?.criticalMinor) { mutableStateOf(plainTaka(pol?.criticalMinor ?: 0)) }
    var suspendOn by rememberSaveable(pol?.suspendEnabled) { mutableStateOf(pol?.suspendEnabled ?: false) }
    var notifyOn by rememberSaveable(pol?.notifyEnabled) { mutableStateOf(pol?.notifyEnabled ?: true) }
    var saveDialog by rememberSaveable { mutableStateOf(false) }
    var days by rememberSaveable { mutableStateOf(3) }
    var overrideFor by remember { mutableStateOf<DueRow?>(null) }

    AppScaffold(vm, 0, "বকেয়ার সীমা ও অবস্থা", if (isMain) "মূল অ্যাডমিন" else "অ্যাডমিন") {
        MessageText(vm)
        if (board == null) {
            Text("লোড হচ্ছে...")
        } else {
            val critical = board.rows.count { it.status == "CRITICAL" }
            val warning = board.rows.count { it.status == "WARNING" }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("জরুরি", Money.bn(critical.toLong()) + " জন", Modifier.weight(1f))
                StatCard("সতর্কতা", Money.bn(warning.toLong()) + " জন", Modifier.weight(1f))
            }

            SectionTitle("নীতি ও সীমা")
            AppCard {
                if (isMain) {
                    Text("সীমা ফাঁকা বা ০ রাখলে ওই স্তর বন্ধ থাকে। দুটোই বন্ধ থাকলে কোনো সতর্কতা বা স্থগিত হবে না।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Field("সতর্কতা শুরু (টাকা)", warnText, { warnText = it }, KeyboardType.Decimal, hint = "যেমন 1000")
                    Field("জরুরি শুরু (টাকা)", critText, { critText = it }, KeyboardType.Decimal, hint = "যেমন 3000")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = notifyOn, onCheckedChange = { notifyOn = it })
                        Text("সীমা পার হলে সদস্য ও অ্যাডমিনকে বিজ্ঞপ্তি")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = suspendOn, onCheckedChange = { suspendOn = it })
                        Text("জরুরি স্তরে মিল স্থগিত (ঐচ্ছিক)")
                    }
                    Text("স্থগিত থাকলে ওই দিনগুলোর মিল গোনা হয় না। বকেয়া কমলে নিজে চালু হয়। অ্যাডমিন কারণ দিয়ে ছাড় দিতে পারেন।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryButton("নীতি সংরক্ষণ", !vm.busy) { saveDialog = true }
                } else {
                    KeyValue("সতর্কতা শুরু", if (board.policy.warningMinor > 0) Money.format(board.policy.warningMinor) else "বন্ধ")
                    KeyValue("জরুরি শুরু", if (board.policy.criticalMinor > 0) Money.format(board.policy.criticalMinor) else "বন্ধ")
                    KeyValue("মিল স্থগিত", if (board.policy.suspendEnabled) "চালু" else "বন্ধ")
                    Text("সীমা বদলাতে পারেন শুধু মূল অ্যাডমিন।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionTitle("সাময়িক ছাড়ের মেয়াদ")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 7, 14).forEach { d -> ChoiceChip(Money.bn(d.toString()) + " দিন", days == d) { days = d } }
            }

            SectionTitle("সদস্যদের অবস্থা")
            board.rows.forEach { r ->
                AppCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.name} (${r.code})", fontWeight = FontWeight.Bold)
                        StatusChip(dueLabel(r.status), positive = r.status == "CLEAR")
                    }
                    Text("মোট বকেয়া: " + Money.format(r.totalDueMinor))
                    if (r.suspended) Text("মিল স্থগিত", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    if (r.overrideUntil != null) Text("ছাড় আছে " + Dates.bnDate(r.overrideUntil) + " পর্যন্ত", fontSize = 12.sp)
                    if (r.status == "CRITICAL" || r.suspended) {
                        OutlinedButton(onClick = { overrideFor = r }, enabled = !vm.busy) {
                            Text("সাময়িক ছাড় দিন (" + Money.bn(days.toString()) + " দিন)")
                        }
                    }
                }
            }
            Text(
                "এই তালিকা শুধু অ্যাডমিন দেখতে পান। সদস্যরা কেবল নিজের অবস্থা দেখেন, কোনো সার্বজনীন তালিকা বা র‍্যাঙ্কিং নেই।",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (saveDialog) {
        ReasonDialog(
            "নীতি সংরক্ষণ",
            { reason -> vm.saveDuePolicy(warnText, critText, suspendOn, notifyOn, reason); saveDialog = false },
            { saveDialog = false }
        )
    }
    overrideFor?.let { r ->
        ReasonDialog(
            "সাময়িক ছাড়: " + r.name,
            { reason -> vm.overrideSuspension(r.id, days, reason); overrideFor = null },
            { overrideFor = null }
        )
    }
}
