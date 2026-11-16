package ar.edu.uade.go2rondas.domain.model

data class Robot(
    val id: String,
    val name: String,
    val source: RobotSource,
    /** Dirección del servidor; sólo para robots que no son el simulador local. */
    val address: String? = null
)

enum class RobotSource { LOCAL_SIMULATOR, MUJOCO_SERVER }
