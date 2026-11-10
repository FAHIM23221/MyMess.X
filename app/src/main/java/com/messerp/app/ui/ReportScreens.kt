package com.messerp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messerp.app.data.Dates
import com.messerp.app.data.Role

@Composable
private fun ReportCard(vm: AppViewModel, kind: String, title: String, detail: String) {
    AppCard {
        Text(title, fontWeight = FontWeight.Bold)
        Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.exportReport(kind, false) }, enabled = !vm.busy) { Text("ডাউনলোড (CSV)") }
            OutlinedButton(onClick = { vm.exportReport(kind, true) }, enabled = !vm.busy) { Text("শেয়ার") }
        }
    }
}

@Composable
fun ReportScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    if (p.role == Role.MEMBER) return
    AppScaffold(vm, 0, "রিপোর্ট ও CSV", "অ্যাডমিন") {
        MessageText(vm)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { vm.shiftReportMonth(-1) }) { Text("‹") }
            Text(if (vm.reportMonth.isEmpty()) "চলতি মাস" else Dates.bnMonth(vm.reportMonth), fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { vm.shiftReportMonth(1) }) { Text("›") }
        }
        ReportCard(vm, "MEMBERS", "সদস্য সারসংক্ষেপ", "প্রতি সদস্যের মিল, মিল খরচ, দুই খাতার বর্তমান ব্যালেন্স ও চালের হিসাব।")
        ReportCard(vm, "CASH_MESS", "ক্যাশ বুক: মেস খাতা", "এ মাসের প্রতিটি আয়-ব্যয়, চলমান ব্যালেন্স, আগের জের ও মাস শেষের ব্যালেন্সসহ।")
        ReportCard(vm, "CASH_UTILITY", "ক্যাশ বুক: বিদ্যুৎ ও রাঁধুনি বিল", "এ মাসে এই খাতার সংগ্রহ ও বিল পরিশোধ।")
        ReportCard(vm, "FINES", "জরিমানার তালিকা", "সব জরিমানা: কারণ, মোট, পরিশোধ, বাকি ও অবস্থা (মাস নির্বিশেষে)।")
        Text(
            "ফাইলটি Excel, Google Sheets বা অন্য স্প্রেডশিটে খোলে, বাংলা ঠিক দেখায়। সংখ্যা ইংরেজি অঙ্কে, যাতে যোগ করা যায়। " +
                "ব্যালেন্সে ধনাত্মক মানে বাকি, ঋণাত্মক মানে অগ্রিম। \"বর্তমান ব্যালেন্স\" মানে রিপোর্ট বানানোর মুহূর্তের হিসাব।",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "এই ফাইলে সদস্যদের আর্থিক তথ্য থাকে। শুধু প্রয়োজনে ও বিশ্বস্ত কাউকে পাঠান। প্রতিটি এক্সপোর্ট অডিট লগে থাকে।",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.error
        )
    }
}
