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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.messerp.app.BuildConfig

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None
    )
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
fun AuthColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(24.dp))
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
        if (vm.busy) {
            CircularProgressIndicator()
        } else {
            MessageText(vm)
            Button(onClick = { vm.restore() }) { Text("আবার চেষ্টা করুন") }
        }
    }
}

@Composable
fun LoginScreen(vm: AppViewModel, adminMode: Boolean) {
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var taps by remember { mutableIntStateOf(0) }

    AuthColumn {
        Text(
            if (adminMode) "অ্যাডমিন লগইন" else "Smart Mess",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (!adminMode) Text("স্বচ্ছ হিসাব, সবার জন্য")
        if (!vm.configured) {
            Text("ব্যাকএন্ড কনফিগার করা নেই (SUPABASE_URL ও SUPABASE_ANON_KEY প্রয়োজন)।", color = MaterialTheme.colorScheme.error)
        }
        Field("ফোন নম্বর", phone, { phone = it }, KeyboardType.Phone)
        Field("পাসওয়ার্ড", password, { password = it }, password = true)
        MessageText(vm)
        Button(
            onClick = { vm.login(phone, password, adminMode) },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (vm.busy) "অপেক্ষা করুন..." else if (adminMode) "অ্যাডমিন হিসেবে প্রবেশ" else "লগইন") }
        if (adminMode) {
            TextButton(onClick = { vm.go(Screen.Login) }) { Text("সাধারণ লগইনে ফিরুন") }
        } else {
            TextButton(onClick = { vm.go(Screen.Register) }) { Text("ইনভাইট কোড দিয়ে যোগ দিন") }
            Text(
                "পাসওয়ার্ড ভুলে গেলে অ্যাডমিনের সঙ্গে যোগাযোগ করুন।",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "সংস্করণ ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
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
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var invite by rememberSaveable { mutableStateOf("") }

    AuthColumn {
        Text("নতুন অ্যাকাউন্ট", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Field("পুরো নাম", name, { name = it })
        Field("ফোন নম্বর", phone, { phone = it }, KeyboardType.Phone)
        Field("পাসওয়ার্ড (কমপক্ষে ৮ অক্ষর)", password, { password = it }, password = true)
        Field("পাসওয়ার্ড আবার দিন", confirm, { confirm = it }, password = true)
        Field("ইনভাইট কোড", invite, { invite = it })
        MessageText(vm)
        Button(
            onClick = { vm.register(name, phone, password, confirm, invite) },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (vm.busy) "অপেক্ষা করুন..." else "রেজিস্ট্রেশন") }
        TextButton(onClick = { vm.go(Screen.Login) }) { Text("লগইনে ফিরুন") }
    }
}
