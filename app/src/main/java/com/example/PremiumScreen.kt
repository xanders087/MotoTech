package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun PremiumScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MotoBg)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Star, contentDescription = "Premium", tint = MotoPrimary, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Desbloquea MotoTech Premium",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MotoTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Únete a nuestra capa Premium o B2B (Modo Flotas) para agregar múltiples motocicletas, exportar PDFs de la hoja de vida, adjuntar fotos de facturas y activar alertas automatizadas.",
            fontSize = 16.sp,
            color = MotoTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { /* Unlock Premium */ },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MotoPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Mejorar a Premium", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
