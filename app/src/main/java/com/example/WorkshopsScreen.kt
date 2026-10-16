package com.example

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkshopsScreen() {
    val workshops = listOf(
        "Taller MotoTech Express" to "A 2.5 km - Ajuste de válvulas y sincronización",
        "Riders Garage Certificado" to "A 4.1 km - Mecánica general y electrónica",
        "Centro de Servicio B2B" to "A 5.0 km - Especialistas en flotas y mensajería"
    )

    Scaffold(
        containerColor = MotoBg,
        topBar = {
            TopAppBar(
                title = { Text("Talleres Certificados", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MotoTextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MotoBg)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(workshops.size) { index ->
                val (name, desc) = workshops[index]
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, MotoBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = MotoBg), // Use transparent or MotoBg
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = MotoPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoTextPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MotoTextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(desc, fontSize = 14.sp, color = MotoTextSecondary)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { /* Agendar Cita */ },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Agendar Cita en Taller", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
