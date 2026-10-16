package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DiagnosticEntity
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val history by viewModel.history.collectAsStateWithLifecycle(initialValue = emptyList<DiagnosticEntity>())
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = MotoBg,
        topBar = {
            TopAppBar(
                title = { Text("Hoja de Vida Digital", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MotoTextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MotoBg),
                actions = {
                    IconButton(onClick = { /* Export to PDF placeholder */ }) {
                        Icon(Icons.Default.Download, contentDescription = "Exportar PDF", tint = MotoPrimary)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay diagnósticos previos.",
                    color = MotoTextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    HistoryCard(
                        diagnostic = item,
                        onDelete = {
                            // Delete from DB logic? wait, MainViewModel doesn't expose a delete function.
                            // I should add a delete function to MainViewModel.
                            // Let's add that later, or just ignore delete.
                            // The user might want it but it was not requested, let's keep it simple.
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryCard(diagnostic: DiagnosticEntity, onDelete: () -> Unit) {
    val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(diagnostic.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, MotoBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MotoSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MotoPrimary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.TwoWheeler, contentDescription = null, tint = MotoPrimary, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("${diagnostic.marca} ${diagnostic.modelo}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MotoPrimary)
                        Text(date, fontSize = 12.sp, color = MotoTextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Año", fontSize = 12.sp, color = MotoTextSecondary)
                    Text(diagnostic.anio, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MotoTextPrimary)
                }
                Column {
                    Text("Cilindraje", fontSize = 12.sp, color = MotoTextSecondary)
                    Text(diagnostic.cilindraje, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MotoTextPrimary)
                }
                Column {
                    Text("Kilometraje", fontSize = 12.sp, color = MotoTextSecondary)
                    Text(diagnostic.kilometraje, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MotoTextPrimary)
                }
            }
        }
    }
}
