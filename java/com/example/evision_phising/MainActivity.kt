package com.example.evision_phising

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.evision_phising.ui.theme.Evision_phisingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {//액티비가 생성될 때 호출
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Evision_phisingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // AlertWarn 객체 생성
                    val alert = AlertWarn(this@MainActivity) //이 클래스를 통해 경고를 띄우는 기능, this@MainActivity를 사용하여 MainActivity의 컨텍스트를 전달

                    // Greeting 컴포저블에 클릭 이벤트 추가
                    Greeting(
                        name = "Android",
                        modifier = Modifier
                            .padding(innerPadding)
                            .clickable {
                                // 텍스트 클릭 시 showAlert 호출
                                alert.showAlert("보이스피싱 위험!")
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Evision_phisingTheme {
        Greeting("Android")
    }
}
