# Instructivo de pruebas

Paso a paso para probar la app, reproducir los cinco escenarios del TP (E1 a E5) y las demás situaciones, con los comandos exactos. Hay dos robots:

- **Go2-01, simulador local.** Viene dentro de la app: alcanza con el emulador, sin red ni servidor. Los escenarios se provocan desde el *modo demo* de la app.
- **Go2-02, servidor MuJoCo.** El Go2 camina con física en la PC y se ve en 3D. Los escenarios se provocan desde la consola del servidor.

Índice:

- [Antes de empezar](#antes-de-empezar)
- [Cómo provocar cada escenario](#cómo-provocar-cada-escenario)
- [Pruebas con Go2-01](#pruebas-con-go2-01-simulador-local) (P1 a P10)
- [Pruebas con Go2-02](#pruebas-con-go2-02-servidor-mujoco) (P11 a P22)
- [Pruebas extra](#pruebas-extra)
- [Guion para una demo de 10 minutos](#guion-para-una-demo-de-10-minutos)
- [Si algo no sale](#si-algo-no-sale)
- [Tests automáticos](#tests-automáticos)

## Antes de empezar

Los comandos son para PowerShell, desde la carpeta del repositorio (`tp-hmi-go2-rondas`).

1. **Abrir el emulador.** Desde Android Studio (Device Manager → *Pixel9_API36_noGPS* → Play) o con:

   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pixel9_API36_noGPS
   ```

2. **Instalar la app** (sólo si cambió el código). Al terminar, abrir *Go2 Rondas* en el emulador. Más opciones en el [README](README.md#cómo-compilar).

   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat installDebug
   ```

3. **Levantar el servidor MuJoCo** (sólo para Go2-02; la instalación está en [`server/README.md`](server/README.md)). Está listo cuando dice `escuchando en ws://0.0.0.0:8765` y aparece el `>` para escribir comandos. Con `--viewer` se abre también la ventana 3D del data center.

   ```powershell
   cd server
   .\.venv\Scripts\python.exe -m go2server --viewer
   ```

   Desde el emulador la app encuentra el servidor en `10.0.2.2:8765`. Para un teléfono, ver el [README](README.md#go2-02-con-el-servidor-mujoco).

4. **Elegir el robot** en la app: *Go2-01* o *Go2-02*. En el Panel tiene que decir **Conectado** y **En base**.

5. **Elegir la velocidad.** Para mostrar, x4 se entiende bien. Para no esperar, x10: una ronda completa tarda un par de minutos.
   - Go2-01: ícono de ajustes arriba a la derecha del Panel (*modo demo*) → x1, x4 o x10.
   - Go2-02: en la consola del servidor, `velocidad 4` o `velocidad 10`.

**Antes de cada prueba**, el robot tiene que estar en la base y sin alertas críticas pendientes: con una sin reconocer, la app no deja iniciar otra ronda. Pestaña *Alertas* → tocar cada una → **Reconocer alerta**.

## Cómo provocar cada escenario

Con Go2-01, en el *modo demo* de la app (ícono de ajustes en el Panel → **Activar** en la fila). Con Go2-02, escribiendo el comando en la consola del servidor y Enter. Todos se piden con una ronda en curso: lo que se pide con el robot en la base aparece en la ronda siguiente, y lo que no llegó a aparecer se descarta al terminar la ronda.

| Escenario | Go2-01 (modo demo) | Go2-02 (consola) | Qué hace el robot |
| --- | --- | --- | --- |
| E2. Batería baja | *Batería baja* | `bateria` | Baja al 31 % y descarga rápido. Avisa al 30 %; al 15 % vuelve solo |
| E3. Desconexión | *Corte de conexión* (60 s) | `corte 60` (o `corte 20`) | Termina el checkpoint y espera. Más de 5 min sin enlace: vuelve |
| E4. Obstáculo en el checkpoint | *Obstáculo en el checkpoint* | `obstaculo` | Una caja tapa el próximo punto: frena y pide **Reintentar** o **Saltear** |
| E4. Sacar la caja | — | `despejar` | Después, **Reintentar** se acepta |
| E5. Temperatura crítica | *Temperatura crítica* | `temperatura` | La próxima medición supera el umbral: avisa y sigue |
| Obstáculo en el camino | *Obstáculo en el camino* | `caja` | Lo rodea solo, avisa con foto y sigue |
| Agua en el piso | *Agua en el piso* | `agua` | Lo rodea sin pisarlo, alerta crítica con foto y sigue |
| Humo o fuego | *Humo o fuego* | `humo` | Alerta crítica con foto, corta la ronda y vuelve a la base |
| Humedad fuera de rango | *Humedad fuera de rango* | `humedad` | La próxima medición sale de rango: avisa y sigue |
| Ruido anómalo | *Ruido anómalo* | `ruido` | La próxima medición sale de rango: avisa y sigue |
| Situaciones al azar | Interruptor *Situaciones aleatorias* | `aleatorio` | Cada 45 s a 2 min simulados pasa algo al azar |
| Orden rechazada | *Rechazar la próxima orden* | — | La próxima orden vuelve *rechazada* |
| Orden sin respuesta | *Ignorar la próxima orden* | — | La próxima orden queda *sin respuesta* a los 5 s |

Otros comandos de la consola: `velocidad N` (1 a 10), `estado` (estado, batería y posición del robot) y `ayuda`.

## Pruebas con Go2-01 (simulador local)

Cada prueba arranca igual: Panel → **Elegir misión** → *Ronda completa* → **Continuar** → **Iniciar ronda**, y esperar a que el estado diga *En ronda*. Abajo, «iniciar una ronda» quiere decir eso.

### P1. E1 – Misión normal

1. Iniciar una ronda. La orden aparece *pendiente* y enseguida *confirmada*.
2. Mirar el plano: cada checkpoint inspeccionado se pone verde. Tocar uno para ver su medición (temperatura con su umbral, humedad y ruido).
3. Esperar a que el robot vuelva a la base y tocar **Ver resumen**.

**Esperado:** 8/8 checkpoints, el resumen cierra con *Todo en orden* y en *Historial* la ronda figura *Completada*.

### P2. E2 – Batería baja

1. Iniciar una ronda y, en el modo demo, activar *Batería baja*.
2. Al 30 % aparece la advertencia y **Volver a base** pasa a ser el botón principal. Tocarlo: el estado pasa a *Regresando*.
3. Variante: no tocar nada. Al 15 % el robot vuelve solo.
4. Variante R5: activarla mientras el robot va hacia CP5 (el último punto de planta baja). Antes de la escalera aparece *No alcanza la batería para el subsuelo* con la batería que necesitaría, y el robot vuelve sin bajar.

**Esperado:** la ronda queda *Interrumpida por batería*, con los checkpoints que llegó a hacer.

### P3. E3 – Desconexión

1. Iniciar una ronda y activar *Corte de conexión*.
2. A los pocos segundos aparece el banner *Sin conexión con el robot*: las tarjetas se ponen grises y muestran la antigüedad del dato.
3. Abrir **Controles** y tocar **Pausar**: la orden queda *pendiente* y a los 5 s pasa a *sin respuesta*. La app no la reenvía.
4. A los 60 s vuelve el enlace: el banner se va y los datos se actualizan solos. Si el robot quedó esperando, tocar **Reanudar**.

**Esperado:** en el detalle de la ronda (*Historial*) figuran el corte con su duración y la orden sin respuesta.

### P4. E4 – Obstáculo en el checkpoint

1. Iniciar una ronda y activar *Obstáculo en el checkpoint*.
2. El robot frena y el estado pasa a *Esperando decisión*. Tocar **Ver alerta** → **Reconocer alerta**.
3. Tocar **Reintentar**: la orden vuelve *rechazada* porque el obstáculo sigue ahí.
4. Tocar **Saltear CPn**: el robot sigue al checkpoint siguiente.

**Esperado:** la ronda sigue; en el plano y en el resumen el checkpoint aparece salteado (*1 salteado*).

### P5. E5 – Temperatura crítica

1. Iniciar una ronda y activar *Temperatura crítica*.
2. En la próxima medición llega una alerta crítica con la lectura y el umbral. El robot no frena.
3. Abrir la alerta → **Reconocer alerta** → **Escalar al técnico de guardia** (queda registrado; no es una orden al robot).

**Esperado:** el checkpoint queda en rojo con anomalía y el historial registra el escalamiento.

### P6. Lo que el robot resuelve solo

1. Iniciar una ronda y activar juntos *Obstáculo en el camino*, *Agua en el piso*, *Humedad fuera de rango* y *Ruido anómalo*.
2. Ir a la pestaña *Alertas* mientras avanza.

**Esperado:** llegan *Obstáculo rodeado*, *Agua en el piso*, *Humedad fuera de rango* y *Ruido anómalo*, y el robot nunca pide una decisión. La ronda termina *Completada*.

### P7. Humo o fuego

1. Iniciar una ronda y activar *Humo o fuego*.

**Esperado:** alerta crítica *Humo o fuego*, el estado pasa a *Regresando* sin que nadie toque nada y la ronda queda *Interrumpida*. La alerta ofrece **Escalar al técnico de guardia**.

### P8. Controles: pausar, detener, volver y cancelar

1. Con una ronda en curso, **Controles** → **Pausar**: estado *Pausado*. **Reanudar**: vuelve a *En ronda*.
2. **Detención operativa**: mantener apretado 2 s (*Mantener 2 s para detener*). Estado *Detenido*.
3. **Volver a base**: estado *Regresando*; en *Historial* la ronda queda *Interrumpida*.
4. En otra ronda, **Cancelar misión**: pide confirmación, el robot vuelve y la ronda queda *Cancelada*.

**Esperado:** *Volver a base* conserva lo inspeccionado (*Interrumpida*); *Cancelar* anula la ronda (*Cancelada*).

### P9. Orden rechazada y orden sin respuesta

1. Activar *Rechazar la próxima orden* y mandar cualquier orden (por ejemplo, **Pausar**): vuelve *rechazada* con el motivo.
2. Activar *Ignorar la próxima orden* y mandar otra: queda *pendiente* y a los 5 s pasa a *sin respuesta*.

### P10. Cámara en vivo sin cámara

1. Panel → **Cámara en vivo**.

**Esperado:** la pantalla explica que el simulador de la app no tiene cámara (las fotos reales salen de Go2-02).

## Pruebas con Go2-02 (servidor MuJoCo)

Conviene tener a la vista el emulador, la ventana 3D y la consola del servidor. Los comandos se escriben con la ronda en curso y el robot caminando hacia un checkpoint: la caja, el agua y el humo aparecen por delante, en un tramo recto. Con `velocidad 4` hay tiempo para ver cada cosa.

### P11. Conexión y mapa del sitio

1. Elegir **Go2-02** en la app.
2. Ir a *Historial*.

**Esperado:** el Panel dice *Go2-02 · servidor MuJoCo* y **Conectado**; el Historial dice *Rondas en Data center – Edificio A*: el mapa lo mandó el robot.

### P12. E1 con física y fotos

1. Iniciar una ronda y mirar la ventana 3D: el Go2 trota por el pasillo, baja la escalera al subsuelo y vuelve.
2. Sobre cada checkpoint, el disco se pone amarillo mientras mide y verde al terminar, con la medición escrita encima.
3. Al terminar, *Historial* → la ronda → tocar un checkpoint.

**Esperado:** ronda *Completada*; el detalle de cada checkpoint muestra la foto de la cámara frontal.

### P13. Caja en el camino

1. Con el robot caminando, escribir `caja`.

**Esperado:** aparece una caja naranja delante; el robot la rodea por un costado. Llega la advertencia *Obstáculo rodeado* con la foto y la ronda sigue.

### P14. Agua en el piso

1. Con el robot caminando, escribir `agua`.
2. Pestaña *Alertas* → *Agua en el piso*.

**Esperado:** aparece un charco azul; el robot lo rodea sin pisarlo y sigue. La alerta es crítica y trae la foto del charco (*Cámara frontal del robot*).

### P15. Cámara en vivo

1. Panel → **Cámara en vivo**.

**Esperado:** se ve lo que ve el robot, unas 4 imágenes por segundo, con *En vivo · cámara frontal*. No se guarda. Con el enlace cortado la imagen deja de actualizarse y lo dice.

### P16. E4 con el LiDAR y `despejar`

1. Con el robot caminando, escribir `obstaculo`. La caja aparece sobre el próximo checkpoint que se pueda rodear (CP2 está en una esquina: si el próximo es CP2, va al siguiente).
2. El LiDAR frena al robot a 0,9 m de la caja y el estado pasa a *Esperando decisión*.
3. Abrir la alerta: trae la foto de la caja. **Reconocer alerta**.
4. **Reintentar**: vuelve *rechazada* porque el LiDAR la sigue viendo.
5. Escribir `despejar` en la consola: la caja desaparece.
6. **Reintentar** otra vez: se acepta y el estado vuelve a *En ronda*.

Variante: en el paso 4 tocar **Saltear CPn**. El robot rodea la caja por un costado y sigue; el checkpoint queda gris en la ventana 3D y en el detalle de ese checkpoint aparece la foto del incidente.

### P17. E5 – Temperatura crítica

1. Escribir `temperatura`.

**Esperado:** en la próxima medición llega *Temperatura alta* (crítica) con la foto del punto; el robot no frena. El checkpoint queda rojo en la ventana 3D.

### P18. E3 – Corte de enlace

1. Escribir `corte 20`.

**Esperado:** a los pocos segundos la app muestra *Sin conexión*. A los 20 s vuelve a *Conectado* y llegan los eventos que el robot guardó durante el corte.

### P19. E2 – Batería baja

1. Escribir `bateria`.

**Esperado:** igual que en P2: advertencia al 30 %, vuelve solo al 15 % y la ronda queda *Interrumpida por batería*.

### P20. Humo o fuego, con foto

1. Iniciar una ronda y, cuando el robot esté caminando, escribir `humo`.
2. Abrir la alerta *Humo o fuego*.
3. Esperar a que llegue a la base y abrir la ronda en *Historial*.

**Esperado:** aparece un foco de fuego con una columna de humo al costado del pasillo. La alerta es crítica y su foto muestra el fuego y el humo. El robot vuelve sin acercarse (*Regresando* → *En base*). La ronda queda *Interrumpida* y en el resumen la alerta tiene **Ver foto**.

### P21. Servidor apagado en medio de una ronda

1. Con una ronda en curso, cerrar el servidor con Ctrl+C.
2. La app muestra *Sin conexión* y reintenta cada 3 s.
3. Volver a levantar el servidor (mismo comando de [Antes de empezar](#antes-de-empezar)).

**Esperado:** la app se reconecta sola. El robot arranca de nuevo en la base, así que la ronda que quedó abierta aparece *Sin cerrar* en el Historial.

### P22. Situaciones al azar

1. Escribir `aleatorio` (otra vez para apagarlo) e iniciar una ronda.

**Esperado:** cada 45 s a 2 min simulados pasa algo sin aviso (una medición fuera de rango, una caja, agua, un corte, batería baja o humo). Sirve para mostrar la app como en una noche real.

## Pruebas extra

- **Notificaciones:** la primera vez la app pide permiso para notificar (Android 13 o más). Con una ronda en curso, provocar un escenario y salir de la app con el botón de inicio. La alerta llega como notificación; al tocarla se abre su detalle. Con la app a la vista no notifica, porque el Panel ya muestra la alerta.
- **Cambiar de sitio sin recompilar:** el ejemplo `sitios/datacenter-edificio-b.json` es un data center de una sola planta con 5 checkpoints.
  - Go2-01: subir el archivo al emulador y, con el robot en la base, ir a modo demo → *Cargar archivo…* → Descargas. El Panel muestra el plano nuevo y en Misiones sólo aparece la ronda completa. *Usar el de ejemplo* vuelve al Edificio A.

    ```powershell
    & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" push sitios/datacenter-edificio-b.json /sdcard/Download/
    ```

  - Go2-02: levantar el servidor con el otro sitio. Al conectarse, la app recibe el plano nuevo y la vista 3D muestra la otra escena.

    ```powershell
    cd server
    .\.venv\Scripts\python.exe -m go2server --viewer --sitio ../sitios/datacenter-edificio-b.json
    ```

  - El Historial y la "última ronda" son por sitio: con el Edificio B aparecen vacíos, y al volver al de ejemplo reaparecen las rondas del Edificio A.
  - Un archivo inválido (por ejemplo, un checkpoint fuera del recorrido) se rechaza con el motivo, y el robot sigue con el sitio que tenía.
- **Cada robot tiene su historial:** hacer una ronda con cada uno y cambiar de robot; el Historial y la "última ronda" del Panel muestran sólo las del robot elegido.

## Guion para una demo de 10 minutos

Los cinco escenarios del TP entran en una sola ronda de Go2-02 a x4, más E2 al final con Go2-01. Antes de empezar: servidor levantado, app en Go2-02 con el Panel *En base*, alertas reconocidas y la ventana 3D al lado del emulador.

1. **0:00 – E1.** Consola: `velocidad 4`. App: iniciar una ronda completa. Mostrar la orden *pendiente* → *confirmada* y al Go2 trotando en la ventana 3D.
2. **1:00 – Lo que resuelve solo.** Consola: `caja`. El robot la rodea y llega *Obstáculo rodeado*. Explicar el criterio: si puede resolverlo, saca foto, avisa y sigue.
3. **2:00 – Agua y cámara.** Consola: `agua`. Abrir la alerta crítica con la foto del charco. Volver al Panel y abrir **Cámara en vivo**.
4. **3:30 – E4.** Consola: `obstaculo`. Cuando diga *Esperando decisión*, abrir la alerta con la foto de la caja, **Reconocer alerta**, **Reintentar** (rechazada), consola `despejar`, **Reintentar** (aceptada).
5. **5:30 – E5.** Consola: `temperatura`. Alerta crítica: **Reconocer alerta** y **Escalar al técnico de guardia**.
6. **6:30 – E3.** Consola: `corte 20`. Mostrar el banner *Sin conexión*, los datos en gris y cómo vuelve solo.
7. **7:30 – Humo.** Consola: `humo`. Abrir la alerta: la foto muestra el fuego. El robot vuelve solo a la base.
8. **8:30 – Historial.** Abrir la ronda *Interrumpida*: eventos, **Ver foto** en cada alerta y lo que conviene hacer después.
9. **9:00 – E2 con Go2-01.** Cambiar a Go2-01, modo demo a x10, iniciar una ronda y activar *Batería baja*: advertencia al 30 % y vuelta sola al 15 %.

Si la ronda termina antes de llegar a un paso, iniciar otra y seguir desde ese paso.

## Si algo no sale

| Qué pasa | Por qué | Qué hacer |
| --- | --- | --- |
| No deja iniciar: *Reconocela antes de iniciar otra ronda* | Hay una alerta crítica sin reconocer | Pestaña *Alertas* → cada una *Sin reconocer* → **Reconocer alerta** |
| Go2-02 dice *Sin conexión* | El servidor está apagado | Levantarlo ([Antes de empezar](#antes-de-empezar), paso 3); la app reintenta cada 3 s |
| El servidor no arranca: el puerto 8765 está ocupado | Ya hay otro servidor corriendo | Cerrar el otro con Ctrl+C en su terminal |
| La consola dice *va a aparecer en la próxima ronda* | El robot está en la base | Iniciar una ronda: aparece cuando el robot salga |
| Un comando no hace nada visible | El robot está volviendo, o el próximo tramo es muy corto | Esperar a que camine hacia el siguiente checkpoint; al terminar la ronda, lo que no apareció se descarta |
| La caja de `obstaculo` no aparece enseguida | Va sobre el próximo checkpoint que se pueda rodear | Esperar al tramo siguiente |
| Una advertencia no se ve en el Panel | El Panel muestra sólo lo importante | Verla en la pestaña *Alertas* |
| La ronda termina antes de probar todo | x10 es muy rápido | `velocidad 4` (Go2-02) o x4 en el modo demo (Go2-01) |
| No se abre la ventana 3D | Faltó `--viewer` | Levantar el servidor con `--viewer` |
| Una ronda figura *Sin cerrar* | Se reinició el robot o el servidor en medio de la ronda | Es lo esperado: revisar qué puntos alcanzó a inspeccionar |

## Tests automáticos

Desde la carpeta del repositorio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
```

```powershell
cd server
.\.venv\Scripts\python.exe -m unittest discover -s tests
```
