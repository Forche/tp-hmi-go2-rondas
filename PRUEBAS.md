# Instructivo de pruebas

Paso a paso para probar la app y reproducir los cinco escenarios del TP. Hay dos formas:

- **A. Go2-01, simulador local.** Sólo hace falta el APK: sin red, sin servidor. Los escenarios se provocan desde el modo demo de la app.
- **B. Go2-02, servidor MuJoCo.** El robot corre en la PC y se ve en 3D. Los escenarios se provocan desde la consola del servidor.

## Preparación

1. Compilar e instalar el APK (ver [README](README.md#cómo-compilar)), o abrir el proyecto en Android Studio y darle Run sobre un emulador.
2. Sólo para la forma B: instalar el servidor siguiendo [`server/README.md`](server/README.md) y levantarlo con la vista 3D:

   ```bash
   cd server
   .venv/Scripts/python -m go2server --viewer
   ```

   La consola queda esperando comandos (`>`). Desde el emulador la app encuentra el servidor en `10.0.2.2:8765`; para un teléfono, ver [README](README.md#go2-02-con-el-servidor-mujoco).

3. En la app, elegir **Go2-01** (forma A) o **Go2-02** (forma B). En el Panel tiene que decir **Conectado**.

Con la forma A conviene subir la velocidad a x10 en el modo demo (ícono de ajustes arriba a la derecha del Panel); con la B, escribir `velocidad 10` en la consola. Una ronda completa tarda así un par de minutos.

## E1. Misión normal

1. Panel → **Elegir misión** → *Ronda completa* → **Continuar**. La hoja muestra las condiciones de inicio, todas en verde.
2. **Iniciar ronda**. La orden aparece *pendiente* y enseguida *confirmada*; el estado pasa a *En ronda*.
3. Mirar el plano: cada checkpoint inspeccionado se pone verde. Tocar uno (por ejemplo el 1) abre su medición: temperatura con su umbral, humedad y ruido. Volver con la flecha.
4. En la forma B, en la ventana de MuJoCo el disco del checkpoint se pone amarillo mientras mide y verde al terminar, con la medición escrita encima.
5. Al terminar, el robot vuelve a la base y el Panel ofrece **Ver resumen**: 8/8, sin anomalías.

**Esperado:** en **Historial** la ronda aparece *Completada*.

## E2. Batería baja

1. Iniciar una ronda completa.
2. Provocar el escenario: forma A, modo demo → *Batería baja*; forma B, consola → `bateria`. La batería pasa a 31 % y se descarga más rápido.
3. Al llegar a 30 % aparece la advertencia y **Volver a base** pasa a ser el botón principal. Tocarlo: la orden queda confirmada y el estado pasa a *Regresando*.
4. Variante: no hacer nada. Al 15 % el robot vuelve solo.

**Esperado:** la ronda queda *Interrumpida por batería* en el historial, con los checkpoints que llegó a hacer.

## E3. Desconexión

1. Iniciar una ronda completa.
2. Cortar el enlace: forma A, modo demo → *Corte de conexión* (60 s); forma B, consola → `corte 60`.
3. A los pocos segundos aparece el banner *Sin conexión con el robot*, las tarjetas se ponen grises y muestran la antigüedad del dato.
4. Abrir **Controles** y tocar **Pausar**: la orden queda *pendiente* y a los 5 s pasa a *sin respuesta*. La app no la reenvía.
5. Cuando vuelve el enlace, el banner desaparece y los datos se actualizan solos. Si el robot quedó en pausa por el corte, tocar **Reanudar**.

**Esperado:** en el detalle de la ronda (Historial) figuran el corte con su duración y la orden sin respuesta.

## E4. Incidente: obstáculo

1. Iniciar una ronda completa.
2. Poner el obstáculo: forma A, modo demo → *Obstáculo*; forma B, consola → `obstaculo`. Se ubica antes del próximo checkpoint.
3. El robot se detiene, el estado pasa a *Esperando decisión* y arriba del Panel aparece la alerta. Tocar **Ver alerta** → **Reconocer alerta**.
4. Elegir **Reintentar**: la orden vuelve *rechazada* ("obstáculo persistente").
5. Elegir **Saltear CPn**: el robot sigue al checkpoint siguiente.

**Esperado:** en el plano y en el resumen el checkpoint salteado aparece tachado (gris en MuJoCo).

## E5. Escenario propio: temperatura crítica

1. Iniciar una ronda completa.
2. Forma A, modo demo → *Temperatura crítica*; forma B, consola → `temperatura`. La próxima medición supera el umbral crítico de su zona por 1,1 °C.
3. Al medir llega una alerta crítica con la lectura y el umbral; el robot espera.
4. **Reconocer alerta** → **Quedarse vigilando**: el robot repite la medición 3 veces, cada 10 s de tiempo simulado.
5. **Escalar al técnico de guardia**: queda registrado en el historial (no es una orden al robot).
6. **Continuar**: la ronda sigue.

**Esperado:** el resumen marca la anomalía en ese checkpoint (rojo en el plano y en MuJoCo) y el historial registra el escalamiento.

## Pruebas extra

- **Orden rechazada / sin respuesta (sólo forma A):** modo demo → *Rechazar la próxima orden* o *Ignorar la próxima orden*, y después mandar cualquier orden.
- **Servidor apagado (forma B):** cerrar el servidor con Ctrl+C. La app muestra *Sin conexión* y reintenta cada 3 s; al volver a levantarlo se reconecta sola.
- **Cada robot tiene su historial:** hacer una ronda con cada uno y cambiar de robot; el Historial y la "última ronda" del Panel muestran sólo las del robot elegido.

## Tests automáticos

```bash
./gradlew testDebugUnitTest
```

```bash
cd server && .venv/Scripts/python -m unittest discover -s tests
```
