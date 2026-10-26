package com.messerp.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messerp.app.BuildConfig

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    hint: String = ""
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { if (hint.isNotEmpty()) Text(hint) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None
        )
    }
}

@Composable
fun MessageText(vm: AppViewModel) {
    val m = vm.message ?: return
    Text(
        m,
        color = if (vm.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    )
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun AuthColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
    }
}

@Composable
fun SplashScreen(vm: AppViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogoMark(72.dp)
        Spacer(Modifier.height(24.dp))
        if (vm.busy) {
            CircularProgressIndicator()
        } else {
            MessageText(vm)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.restore() }, shape = RoundedCornerShape(12.dp)) { Text("আবার চেষ্টা করুন") }
        }
    }
}

@Composable
fun LoginScreen(vm: AppViewModel, adminMode: Boolean) {
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var taps by remember { mutableIntStateOf(0) }

    AuthColumn {
        Spacer(Modifier.height(24.dp))
        LogoMark()
        Text(
            if (adminMode) "অ্যাডমিন লগইন" else "স্মার্ট ট্রান্সপারেন্ট\nমেস",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp
        )
        Text(
            if (adminMode) "সার্ভার আপনার রোল যাচাই করবে।" else "স্বচ্ছ হিসাব, সবার জন্য",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!vm.configured) {
            Text("ব্যাকএন্ড কনফিগার করা নেই (SUPABASE_URL ও SUPABASE_ANON_KEY প্রয়োজন)।", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Field("ফোন নম্বর", phone, { phone = it }, KeyboardType.Phone, hint = "01XXXXXXXXX")
        Field("পাসওয়ার্ড", password, { password = it }, password = true, hint = "••••••••")
        MessageText(vm)
        PrimaryButton(
            text = if (vm.busy) "অপেক্ষা করুন..." else if (adminMode) "অ্যাডমিন হিসেবে প্রবেশ" else "লগইন",
            enabled = !vm.busy,
            onClick = { vm.login(phone, password, adminMode) }
        )
        if (adminMode) {
            SecondaryButton("সাধারণ লগইনে ফিরুন") { vm.go(Screen.Login) }
        } else {
            Text(
                "পাসওয়ার্ড ভুলে গেলে অ্যাডমিনকে বলুন, তিনি সাহায্য করবেন।",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            SecondaryButton("ইনভাইট কোড দিয়ে যোগ দিন") { vm.go(Screen.Register) }
            Text(
                "অ্যাপ সংস্করণ ${BuildConfig.VERSION_NAME}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .clickable {
                        taps++
                        if (taps >= 7) {
                            taps = 0
                            vm.go(Screen.AdminLogin)
                        }
                    }
            )
        }
    }
}

@Composable
fun RegisterScreen(vm: AppViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var fatherName by rememberSaveable { mutableStateOf("") }
    var fatherPhone by rememberSaveable { mutableStateOf("") }
    var motherName by rememberSaveable { mutableStateOf("") }
    var motherPhone by rememberSaveable { mutableStateOf("") }
    var room by rememberSaveable { mutableStateOf("") }
    var seat by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var invite by rememberSaveable { mutableStateOf("") }

    AuthColumn {
        LogoMark(48.dp)
        Text("নতুন অ্যাকাউন্ট", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("অ্যাডমিনের দেওয়া ইনভাইট কোড লাগবে।", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Field("পুরো নাম", name, { name = it })
        Field("ফোন নম্বর", phone, { phone = it }, KeyboardType.Phone, hint = "01XXXXXXXXX")
        Field("রুম নম্বর", room, { room = it })
        Field("সিট নম্বর", seat, { seat = it })
        Text("অভিভাবকের তথ্য", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            "বাবা বা মা, কমপক্ষে একজনের নাম ও ফোন নম্বর দিতে হবে। চাইলে দুজনেরই দিতে পারেন। দিতে না চাইলে মূল অ্যাডমিনের সঙ্গে যোগাযোগ করুন, তিনি বিশেষ কোড দেবেন।",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Field("বাবার নাম", fatherName, { fatherName = it })
        Field("বাবার ফোন নম্বর", fatherPhone, { fatherPhone = it }, KeyboardType.Phone, hint = "01XXXXXXXXX")
        Field("মায়ের নাম", motherName, { motherName = it })
        Field("মায়ের ফোন নম্বর", motherPhone, { motherPhone = it }, KeyboardType.Phone, hint = "01XXXXXXXXX")
        Field("পাসওয়ার্ড (কমপক্ষে ৮ অক্ষর)", password, { password = it }, password = true)
        Field("পাসওয়ার্ড আবার দিন", confirm, { confirm = it }, password = true)
        Field("ইনভাইট কোড", invite, { invite = it })
        MessageText(vm)
        PrimaryButton(
            text = if (vm.busy) "অপেক্ষা করুন..." else "রেজিস্ট্রেশন",
            enabled = !vm.busy,
            onClick = {
                vm.register(
                    name, phone, fatherName, fatherPhone, motherName, motherPhone,
                    room, seat, password, confirm, invite
                )
            }
        )
        SecondaryButton("লগইনে ফিরুন") { vm.go(Screen.Login) }
    }
}
