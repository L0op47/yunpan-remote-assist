package dev.remoteassist

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.remoteassist.agent.AgentScreen
import dev.remoteassist.controller.ControllerScreen
import dev.remoteassist.ui.FeedbackButton
import dev.remoteassist.ui.theme.RemoteassistTheme

private const val PREFS = "yunpan"
private const val KEY_ROLE = "role"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemoteassistTheme {
                var role by remember {
                    mutableStateOf(
                        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ROLE, null)
                    )
                }
                fun setRole(newRole: String) {
                    getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(KEY_ROLE, newRole).apply()
                    role = newRole
                }
                fun clearRole() {
                    getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().remove(KEY_ROLE).apply()
                    role = null
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val screenModifier = Modifier.padding(innerPadding)
                    when (role) {
                        "agent" -> AgentScreen(modifier = screenModifier, onSwitchRole = ::clearRole)
                        "controller" -> ControllerScreen(modifier = screenModifier, onSwitchRole = ::clearRole)
                        else -> RolePicker(modifier = screenModifier, onPick = ::setRole)
                    }
                }
            }
        }
    }
}

/** 开局角色选择：一台手机当被控端，另一台当控制端。 */
@Composable
private fun RolePicker(modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("云畔", style = MaterialTheme.typography.headlineLarge)
        Text("这台手机要扮演什么角色？", style = MaterialTheme.typography.bodyLarge)
        FeedbackButton(
            onClick = { onPick("agent") },
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) {
            Text("我是被控端（让别人帮我）", style = MaterialTheme.typography.titleMedium)
        }
        FeedbackButton(
            onClick = { onPick("controller") },
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) {
            Text("我是控制端（我去帮别人）", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "被控端需要本人授权后才会开始工作；控制端发起连接后也需被控端确认。",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
