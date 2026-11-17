# Go2 Rondas

App Android para operar y supervisar un robot **Unitree Go2 EDU** que hace rondas nocturnas de inspección en un data center.
Trabajo práctico grupal de Desarrollo de Aplicaciones I (UADE): interfaz HMI/HRI con telemetría simulada.

El robot se simula **dentro del APK**: la app funciona sin robot, sin servidor y sin internet.

- Prototipo en Figma: <https://www.figma.com/design/KC4Az4VPV1sjr4UUVoTD7K>

## Cómo compilar

Requiere Android Studio (o su JDK) y el SDK de Android 37.

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. En Windows sin `java` en el PATH:
`$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` antes de `gradlew.bat`.

Tests unitarios (reglas de habilitación de órdenes, geometría del recorrido y simulador):

```bash
./gradlew testDebugUnitTest
```

## Cómo se usa

1. **Elegí un robot.** Go2-01 es el simulador local. Go2-02 representa un robot detrás de un servidor MuJoCo; su cliente todavía no está implementado, así que queda sin conexión.
2. **Panel:** estado, conexión, batería, plano con la posición del robot y los checkpoints, progreso de la ronda y la última orden con su estado.
3. **Misiones:** elegís la ronda, ves su recorrido y la confirmás en una hoja que muestra cada condición de inicio.
4. **Controles** (desde el Panel): pausar, reanudar, volver a base, cancelar (con confirmación) y la detención operativa, que se mantiene presionada 2 s.
5. **Alertas:** reconocer, decidir cómo sigue la ronda (continuar, vigilar, reintentar, saltear) y escalar al técnico de guardia.
6. **Historial:** rondas guardadas en Room, con el resultado de cada checkpoint y los eventos (alertas, órdenes, cortes de conexión).

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

## Arquitectura

MVVM con capas (Clean Architecture), con la estructura del proyecto de referencia de la cátedra (PokedexAcademy-MVVM-Clean).

```
ar.edu.uade.go2rondas
├── MainActivity.kt, Go2RondasApplication.kt   (@AndroidEntryPoint, @HiltAndroidApp)
├── core/            theme, navigation (Screen), model (UiState), coroutines (ApplicationScope)
├── domain/          Kotlin puro
│   ├── model/       Robot, Telemetry, Alert, Command, SiteConfig, History, RobotSnapshot…
│   ├── repository/  RobotRepository, HistoryRepository, SimulationRepository (interfaces)
│   └── usecase/     casos de uso + EvaluateCommandAvailabilityUseCase (reglas de habilitación)
├── data/
│   ├── source/      RobotDataSource (una implementación por tipo de robot)
│   ├── sim/         RobotSimulator (motor), SimulatedRobotDataSource (enlace), DataCenterSite, RoutePath
│   ├── local/       Room: AppDatabase, entidades, DAOs, HistoryLocalDataSource
│   ├── mapper/      Entity ↔ dominio
│   └── repository/  implementaciones de los repositorios
├── di/              módulos de Hilt (corrutinas, base de datos, repositorios)
└── presentation/    una carpeta por pantalla (Screen + ViewModel) y common/components
```

Cómo fluye un dato:

```
Pantalla → ViewModel → UseCase → RobotRepository (interfaz) ← RobotRepositoryImpl → RobotDataSource → simulador
                                                                     └→ HistoryLocalDataSource → Room
```

## Decisiones

- **Hilt**, como en el proyecto de referencia. El ViewModel se obtiene con `hiltViewModel()` como parámetro por defecto de cada pantalla.
- **Flow + `stateIn`** en todos los ViewModels: la telemetría y el historial cambian solos, así que el estado se deriva de los flujos (`combine` / `flatMapLatest`) en vez de asignarse a mano. `WhileSubscribed(5_000)` corta la recolección con la app en segundo plano.
- **Eventos de una sola vez** (navegar, avisos) por `SharedFlow`; el estado durable (última orden y su resultado) vive en el repositorio.
- **Una fuente de datos por tipo de robot.** El repositorio elige la fuente según el robot seleccionado y además escribe el historial: coordina el robot y Room.
- **Órdenes en el scope de la aplicación**, con timeout de 5 s: si el operador cambia de pantalla, la orden igual termina en *confirmada*, *rechazada* o *sin respuesta*. La app no reenvía órdenes sola.
- **Conexión deducida** por la antigüedad del último dato: el robot no avisa que se desconectó.
- **Reglas de habilitación en el dominio**, con tests. Cada orden deshabilitada explica por qué al tocarla.
- **Room** porque el historial es relacional (ronda → resultados y eventos). Los contadores (inspeccionados, anomalías, salteados) se calculan en SQL. El esquema exportado está en `app/schemas/`.
- **Formato de pantalla en presentación.** El proyecto de referencia pone el formato en el dominio; acá va en presentación (`presentation/common/Labels.kt`) porque el dominio no debe conocer cómo se muestran los datos. No se crearon modelos de UI aparte.

## Limitaciones

- No se probó con un Go2 real. El sitio (data center) es genérico y los umbrales son valores de ejemplo.
- Las fotos de los checkpoints son marcadores: el simulador no genera imágenes.
- El cliente del servidor MuJoCo está previsto pero no implementado.
- La detención de la app es un comando de software: no reemplaza la parada de emergencia física del robot.
- Los mensajes entre app y robot siguen un contrato propio del TP, no el protocolo oficial de Unitree.

## Flujo de trabajo

Git flow: `main` para versiones, `develop` para integrar y una rama `feature/*` por funcionalidad, mergeada con `--no-ff`.
