# Go2 Rondas

App Android para operar y supervisar un robot **Unitree Go2 EDU** que hace rondas nocturnas de inspección en un data center.
Trabajo práctico grupal de Desarrollo de Aplicaciones I (UADE): interfaz HMI/HRI con telemetría simulada.

El robot se simula **dentro del APK**: la app funciona sin robot, sin servidor y sin internet.
Opcionalmente, un segundo robot (Go2-02) se conecta por WebSocket a un servidor con **MuJoCo** que mueve el modelo del Go2 ([`server/`](server/README.md)).

- Prototipo en Figma: <https://www.figma.com/design/KC4Az4VPV1sjr4UUVoTD7K>

## Cómo compilar

Requiere Android Studio (o su JDK) y el SDK de Android 37.

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. En Windows sin `java` en el PATH:
`$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` antes de `gradlew.bat`.

Tests unitarios (reglas de habilitación de órdenes, geometría del recorrido, simulador y contrato JSON):

```bash
./gradlew testDebugUnitTest
```

## Cómo se usa

1. **Elegí un robot.** Go2-01 es el simulador local. Go2-02 es el robot del servidor MuJoCo (ver más abajo).
2. **Panel:** estado, conexión, batería, plano con la posición del robot y los checkpoints, progreso de la ronda y la última orden con su estado.
3. **Misiones:** elegís la ronda, ves su recorrido y la confirmás en una hoja que muestra cada condición de inicio.
4. **Controles** (desde el Panel): pausar, reanudar, volver a base, cancelar (con confirmación) y la detención operativa, que se mantiene presionada 2 s.
5. **Alertas:** reconocer, decidir cómo sigue la ronda (continuar, vigilar, reintentar, saltear) y escalar al técnico de guardia.
6. **Historial:** rondas del robot elegido guardadas en Room, con el resultado de cada checkpoint y los eventos (alertas, órdenes, cortes de conexión).

### Modo demo (los 5 escenarios del TP)

El ícono de ajustes en la barra superior del Panel abre el **modo demo**. Ahí se elige la velocidad de la simulación (x1, x4, x10) y se provocan los escenarios:

| Escenario | Cómo probarlo |
| --- | --- |
| E1. Misión normal | Iniciar una ronda completa y esperar a que vuelva a la base. El Panel ofrece el resumen. |
| E2. Batería baja | Con una ronda en curso, activar *Batería baja*. Aparece la advertencia y *Volver a base* pasa a ser la acción principal. Al 15 % el robot vuelve solo. |
| E3. Desconexión | Activar *Corte de conexión* (60 s). Banner, datos en gris con su antigüedad, órdenes *sin respuesta*. El robot termina el checkpoint y espera; al volver la conexión, *Reanudar*. |
| E4. Incidente | Activar *Obstáculo*. El robot se detiene antes del próximo checkpoint; *Reintentar* vuelve rechazada y *Saltear* sigue la ronda. |
| E5. Escenario propio | Activar *Temperatura crítica*. La próxima medición supera el umbral; el robot espera la decisión: *Quedarse vigilando*, *Continuar* o escalar. |

También se puede forzar que la próxima orden sea rechazada o quede sin respuesta.

El modo demo actúa sobre el simulador local (Go2-01). Con Go2-02 los escenarios se provocan desde la consola del servidor.

### Go2-02 con el servidor MuJoCo

1. Instalar y levantar el servidor siguiendo [`server/README.md`](server/README.md) (`python -m go2server --viewer` abre la vista 3D).
2. Elegir **Go2-02** en la app. Si el servidor no está encendido, la app muestra *Sin conexión* y reintenta cada 3 s.
3. Desde la consola del servidor: `obstaculo`, `temperatura`, `bateria` y `corte [s]` provocan los escenarios E2 a E5; `velocidad N` acelera la simulación.

La dirección del servidor se fija al compilar. Por defecto es `10.0.2.2:8765`, que desde el emulador apunta a la PC. Para un teléfono en la misma red Wi-Fi:

```bash
./gradlew assembleDebug -Pgo2.mujocoAddress=192.168.0.20:8765
```

(o `go2.mujocoAddress=...` en `gradle.properties`). La conexión es `ws://` sin cifrar, permitida sólo en la build de debug.

## Arquitectura

MVVM con capas (Clean Architecture), con la estructura del proyecto de referencia de la cátedra (PokedexAcademy-MVVM-Clean).

```
ar.edu.uade.go2rondas
├── MainActivity.kt, Go2RondasApplication.kt   (@AndroidEntryPoint, @HiltAndroidApp)
├── core/            theme, navigation (Screen), model (UiState), coroutines (ApplicationScope), network
├── domain/          Kotlin puro
│   ├── model/       Robot, Telemetry, Alert, Command, SiteConfig, History, RobotSnapshot…
│   ├── repository/  RobotRepository, HistoryRepository, SimulationRepository (interfaces)
│   └── usecase/     casos de uso + EvaluateCommandAvailabilityUseCase (reglas de habilitación)
├── data/
│   ├── source/      RobotDataSource (una implementación por tipo de robot)
│   ├── sim/         RobotSimulator (motor), SimulatedRobotDataSource (enlace), DataCenterSite, RoutePath
│   ├── remote/      MujocoRobotDataSource (WebSocket con OkHttp) y dto/ (contrato JSON)
│   ├── local/       Room: AppDatabase, entidades, DAOs, HistoryLocalDataSource
│   ├── mapper/      Entity ↔ dominio, mensajes JSON ↔ dominio
│   └── repository/  implementaciones de los repositorios
├── di/              módulos de Hilt (corrutinas, red, base de datos, repositorios)
└── presentation/    una carpeta por pantalla (Screen + ViewModel) y common/components
```

Cómo fluye un dato:

```
Pantalla → ViewModel → UseCase → RobotRepository (interfaz) ← RobotRepositoryImpl → RobotDataSource → simulador local
                                                                     │                             └→ servidor MuJoCo (WebSocket)
                                                                     └→ HistoryLocalDataSource → Room
```

## Decisiones

- **Hilt**, como en el proyecto de referencia. El ViewModel se obtiene con `hiltViewModel()` como parámetro por defecto de cada pantalla.
- **Flow + `stateIn`** en todos los ViewModels: la telemetría y el historial cambian solos, así que el estado se deriva de los flujos (`combine` / `flatMapLatest`) en vez de asignarse a mano. `WhileSubscribed(5_000)` corta la recolección con la app en segundo plano.
- **Eventos de una sola vez** (navegar, avisos) por `SharedFlow`; el estado durable (última orden y su resultado) vive en el repositorio.
- **Una fuente de datos por tipo de robot.** El repositorio elige la fuente según el robot seleccionado y además escribe el historial: coordina el robot y Room.
- **Órdenes en el scope de la aplicación**, con timeout de 5 s: si el operador cambia de pantalla, la orden igual termina en *confirmada*, *rechazada* o *sin respuesta*. La app no reenvía órdenes sola.
- **Conexión deducida** por la antigüedad del último dato: el robot no avisa que se desconectó. Vale igual para el simulador y para el servidor MuJoCo.
- **WebSocket con OkHttp y kotlinx.serialization** para Go2-02. El campo `type` de cada mensaje elige la clase; los campos y valores que la app no conoce se ignoran, así un servidor más nuevo no la rompe. Si el socket se cae, se reintenta cada 3 s; una orden enviada sin respuesta termina en *sin respuesta* por el mismo timeout del repositorio.
- **Reglas de habilitación en el dominio**, con tests. Cada orden deshabilitada explica por qué al tocarla.
- **Room** porque el historial es relacional (ronda → resultados y eventos). Los contadores (inspeccionados, anomalías, salteados) se calculan en SQL. El esquema exportado está en `app/schemas/`.
- **Formato de pantalla en presentación.** El proyecto de referencia pone el formato en el dominio; acá va en presentación (`presentation/common/Labels.kt`) porque el dominio no debe conocer cómo se muestran los datos. No se crearon modelos de UI aparte.

## Limitaciones

- No se probó con un Go2 real. El sitio (data center) es genérico y los umbrales son valores de ejemplo.
- Las fotos de los checkpoints son marcadores: el simulador no genera imágenes.
- En el servidor MuJoCo el movimiento es cinemático: el modelo del Go2 se ubica sobre el recorrido, pero no camina con control de patas.
- La conexión con el servidor no está cifrada ni autenticada (`ws://`, sólo en debug). Una instalación real usaría `wss://` con certificado.
- La detención de la app es un comando de software: no reemplaza la parada de emergencia física del robot.
- Los mensajes entre app y robot siguen un contrato propio del TP, no el protocolo oficial de Unitree.

## Flujo de trabajo

Git flow: `main` para versiones, `develop` para integrar y una rama `feature/*` por funcionalidad, mergeada con `--no-ff`.
