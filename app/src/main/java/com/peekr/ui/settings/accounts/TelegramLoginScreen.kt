package com.peekr.ui.settings.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.peekr.data.remote.telegram.TelegramAuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramLoginScreen(
    navController: NavController,
    viewModel: TelegramLoginViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    var phoneNumber by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(authState) {
        when (authState) {
            is TelegramAuthState.Authorized -> navController.popBackStack()
            is TelegramAuthState.Error      -> isLoading = false
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ربط تليجرام") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Telegram icon
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = Color(0xFF0088CC).copy(alpha = 0.15f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Send, null, tint = Color(0xFF0088CC), modifier = Modifier.size(40.dp))
                }
            }

            when (authState) {
                // ==============================
                // Idle — اختر طريقة الربط
                // ==============================
                is TelegramAuthState.Idle -> {
                    Text("اختر طريقة الربط", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                    // Bot Token card (موصى به)
                    Card(
                        onClick = { navController.navigate("settings/apikeys") },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF0088CC).copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0088CC).copy(alpha = 0.15f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Android, null, tint = Color(0xFF0088CC))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Bot Token", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Surface(shape = RoundedCornerShape(50), color = Color(0xFF4CAF50)) {
                                        Text("موصى به", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall, color = Color.White)
                                    }
                                }
                                Text("للقنوات العامة — سهل وسريع\nاحصل على Token من @BotFather",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // User account card
                    Card(
                        onClick = { viewModel.initialize() },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("حساب شخصي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("يحتاج API ID + Hash من my.telegram.org",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ==============================
                // WaitingPhone
                // ==============================
                is TelegramAuthState.WaitingPhone -> {
                    Text("أدخل رقم تليفونك", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("هيتبعتلك كود تحقق على تليجرام", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    OutlinedTextField(
                        value = phoneNumber, onValueChange = { phoneNumber = it },
                        label = { Text("رقم التليفون") }, placeholder = { Text("+201234567890") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { isLoading = true; viewModel.sendPhone(phoneNumber) },
                        modifier = Modifier.fillMaxWidth(), enabled = phoneNumber.isNotEmpty() && !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Text("إرسال الكود")
                    }
                }

                // ==============================
                // WaitingCode
                // ==============================
                is TelegramAuthState.WaitingCode -> {
                    Text("أدخل كود التحقق", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("اتبعتلك كود على تليجرام", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    OutlinedTextField(
                        value = code, onValueChange = { code = it },
                        label = { Text("الكود") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { isLoading = true; viewModel.sendCode(code) },
                        modifier = Modifier.fillMaxWidth(), enabled = code.isNotEmpty() && !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Text("تأكيد")
                    }
                }

                // ==============================
                // WaitingPassword
                // ==============================
                is TelegramAuthState.WaitingPassword -> {
                    Text("أدخل كلمة المرور", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("حسابك محمي بخطوة تحقق ثانية", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("كلمة المرور") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { isLoading = true; viewModel.sendPassword(password) },
                        modifier = Modifier.fillMaxWidth(), enabled = password.isNotEmpty() && !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Text("دخول")
                    }
                }

                // ==============================
                // Error
                // ==============================
                is TelegramAuthState.Error -> {
                    val errMsg = (authState as TelegramAuthState.Error).message

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                                Text("تنبيه", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Text(errMsg, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // زرار Bot Token لو الخطأ له علاقة بـ TDLib أو API
                    if (errMsg.contains("Bot") || errMsg.contains("API") || errMsg.contains("TDLib") || errMsg.contains("BotFather")) {
                        Button(
                            onClick = { navController.navigate("settings/apikeys") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC))
                        ) {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("الذهاب لمفاتيح API")
                        }
                    }

                    OutlinedButton(onClick = { viewModel.initialize() }, modifier = Modifier.fillMaxWidth()) {
                        Text("إعادة المحاولة")
                    }
                }

                // ==============================
                // Authorized
                // ==============================
                is TelegramAuthState.Authorized -> {
                    CircularProgressIndicator()
                    Text("تم الربط — جاري التوجيه...")
                }
            }
        }
    }
}
