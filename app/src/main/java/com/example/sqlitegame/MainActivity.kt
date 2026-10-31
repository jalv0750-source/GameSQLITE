package com.example.sqlitegame

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.sqlitegame.ui.theme.SQLiteGameTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ==========================================
// 1. CAPA DE DATOS (ROOM DATABASE)
// ==========================================

@Entity(tableName = "partida")
data class Partida(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val jugador: String,
    val puntaje: Int
)

@Dao
interface PartidaDao {
    @Insert
    fun insertarPartida(partida: Partida)

    @Query("SELECT * FROM partida ORDER BY puntaje DESC")
    fun mejoressPuntajes(): Flow<List<Partida>>
}

@Database(entities = [Partida::class], version = 1, exportSchema = false)
abstract class GameDatabase : RoomDatabase() {
    abstract fun partidaDao(): PartidaDao

    companion object {
        @Volatile
        private var INSTANCE: GameDatabase? = null

        fun getDatabase(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "game_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ==========================================
// 2. LÓGICA DEL JUEGO (VIEWMODEL & CORRUTINAS)
// ==========================================

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = GameDatabase.getDatabase(application).partidaDao()

    var nombreJugador by mutableStateOf("")
        private set

    var puntaje by mutableIntStateOf(0)
        private set

    var tiempoRestante by mutableIntStateOf(10)
        private set

    var juegoEnCurso by mutableStateOf(false)
        private set

    var juegoTerminado by mutableStateOf(false)
        private set

    val mejoresPuntajes: StateFlow<List<Partida>> = dao.mejoressPuntajes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var timerJob: Job? = null

    fun onNombreChange(nuevoNombre: String) {
        if (!juegoEnCurso) {
            nombreJugador = nuevoNombre
        }
    }

    fun iniciarJuego() {
        if (juegoEnCurso) return

        puntaje = 0
        tiempoRestante = 10
        juegoEnCurso = true
        juegoTerminado = false

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (tiempoRestante > 0) {
                delay(1000)
                tiempoRestante--
            }
            finalizarJuego()
        }
    }

    fun incrementarPuntaje() {
        if (juegoEnCurso) {
            puntaje++
        }
    }

    private fun finalizarJuego() {
        juegoEnCurso = false
        juegoTerminado = true

        val nombreFinal = nombreJugador.ifBlank { "Jugador Anónimo" }
        val nuevaPartida = Partida(jugador = nombreFinal, puntaje = puntaje)

        viewModelScope.launch(Dispatchers.IO) {
            dao.insertarPartida(nuevaPartida)
        }
    }
}

class GameViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(application) as T
        }
        throw IllegalArgumentException("Clase ViewModel desconocida")
    }
}

// ==========================================
// 3. INTERFAZ DE USUARIO (JETPACK COMPOSE)
// ==========================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SQLiteGameTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current.applicationContext as Application
                    val gameViewModel: GameViewModel = viewModel(
                        factory = GameViewModelFactory(context)
                    )
                    GameScreen(viewModel = gameViewModel)
                }
            }
        }
    }
}

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val ranking by viewModel.mejoresPuntajes.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚡ Clicker de 10 Segundos",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Campo para el nombre del jugador
            OutlinedTextField(
                value = viewModel.nombreJugador,
                onValueChange = { viewModel.onNombreChange(it) },
                label = { Text("Nombre del jugador") },
                singleLine = true,
                enabled = !viewModel.juegoEnCurso,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Panel con Temporizador y Puntaje actual
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Tiempo", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = "${viewModel.tiempoRestante}s",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (viewModel.tiempoRestante <= 3 && viewModel.juegoEnCurso) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Puntaje", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = viewModel.puntaje.toString(),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { viewModel.tiempoRestante / 10f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            // Área de Acción / Botón Principal
            if (!viewModel.juegoEnCurso) {
                Button(
                    onClick = { viewModel.iniciarJuego() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = if (viewModel.juegoTerminado) "¡JUGAR DE NUEVO!" else "¡INICIAR JUEGO!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = { viewModel.incrementarPuntaje() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(160.dp)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "¡TOCA\nAQUÍ!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (viewModel.juegoTerminado) {
                Text(
                    text = "🎉 ¡Fin del juego! Puntaje final guardado: ${viewModel.puntaje}",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Lista de Ranking de los mejores puntajes
            Text(
                text = "🏆 Tabla de Mejores Puntajes (Ranking)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            if (ranking.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aún no hay partidas registradas.\n¡Juega la primera partida!",
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    itemsIndexed(ranking) { index, partida ->
                        RankingItem(posicion = index + 1, partida = partida)
                    }
                }
            }
        }
    }
}

@Composable
fun RankingItem(posicion: Int, partida: Partida) {
    val medal = when (posicion) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "#$posicion"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (posicion) {
                1 -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = medal,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(40.dp)
                )
                Text(
                    text = partida.jugador,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            Text(
                text = "${partida.puntaje} pts",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
