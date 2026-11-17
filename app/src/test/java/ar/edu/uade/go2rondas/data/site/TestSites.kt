package ar.edu.uade.go2rondas.data.site

import java.io.File

/** Lee los sitios de ejemplo de la carpeta sitios/ del repositorio (los tests corren desde app/). */
object TestSites {
    private val dir = File("../sitios")

    fun text(name: String = BUILDING_A): String = File(dir, name).readText()

    fun load(name: String = BUILDING_A): SiteDefinition = SiteFile.parse(text(name), requireSimulation = true)

    fun all(): List<File> = dir.listFiles { file -> file.extension == "json" }.orEmpty().toList()

    const val BUILDING_A = "datacenter-edificio-a.json"
    const val BUILDING_B = "datacenter-edificio-b.json"
}
