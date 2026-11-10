package com.cecapi.app.feature.modulo10_accesibilidad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la descripción de imágenes de una captura. Código puro: no necesita Android ni ML Kit. */
class DescriptorImagenesTest {

    private val ancho = 1080
    private val alto = 2400

    @Test
    fun `solo texto no dice nada`() {
        val textos = listOf(ZonaCaptura(0, 0, ancho, alto))
        val etiquetas = listOf(
            EtiquetaImagen("Text", 0.99f),
            EtiquetaImagen("Document", 0.95f),
        )

        assertNull(DescriptorImagenes.descripcion(textos, etiquetas, ancho, alto))
    }

    @Test
    fun `solo imagen con etiquetas confiables describe`() {
        val etiquetas = listOf(
            EtiquetaImagen("Lake", 0.9f),
            EtiquetaImagen("Tree", 0.8f),
            EtiquetaImagen("Bird", 0.7f),
        )

        assertEquals(
            "Veo un lago, un árbol y un pájaro.",
            DescriptorImagenes.descripcion(emptyList(), etiquetas, ancho, alto),
        )
    }

    @Test
    fun `imagen con baja confianza no dice nada`() {
        val etiquetas = listOf(
            EtiquetaImagen("Lake", 0.59f),
            EtiquetaImagen("Tree", 0.4f),
        )

        assertNull(DescriptorImagenes.descripcion(emptyList(), etiquetas, ancho, alto))
    }

    @Test
    fun `texto e imagen juntos describe solo la imagen`() {
        val textos = listOf(ZonaCaptura(0, 0, ancho, 500))
        val etiquetas = listOf(
            EtiquetaImagen("Document", 0.99f),
            EtiquetaImagen("Text", 0.98f),
            EtiquetaImagen("Lake", 0.9f),
            EtiquetaImagen("Tree", 0.8f),
        )

        assertEquals(
            "Veo un lago y un árbol.",
            DescriptorImagenes.descripcion(textos, etiquetas, ancho, alto),
        )
    }

    @Test
    fun `las etiquetas de texto nunca se describen aunque sean confiables`() {
        val etiquetas = listOf(
            EtiquetaImagen("Font", 1f),
            EtiquetaImagen("Screenshot", 1f),
            EtiquetaImagen("Website", 1f),
            EtiquetaImagen("Book", 1f),
            EtiquetaImagen("Publication", 1f),
        )

        assertNull(DescriptorImagenes.descripcion(emptyList(), etiquetas, ancho, alto))
    }

    @Test
    fun `las zonas de texto se amplian, se recortan y se fusionan`() {
        val zonas = DescriptorImagenes.zonasParaTapar(
            listOf(
                ZonaCaptura(10, 10, 50, 50),
                ZonaCaptura(53, 10, 90, 50),
                ZonaCaptura(1000, 2350, 1200, 2500),
            ),
            ancho = 1080,
            alto = 2400,
        )

        assertEquals(2, zonas.size)
        val primera = zonas.first { it.izquierda < 500 }
        assertTrue(primera.izquierda < 10)
        assertTrue(primera.derecha > 90)
        val borde = zonas.first { it.izquierda >= 500 }
        assertEquals(1080, borde.derecha)
        assertEquals(2400, borde.abajo)
    }

    @Test
    fun `una pantalla casi toda de texto no se describe`() {
        val textos = listOf(ZonaCaptura(0, 0, ancho, 1800))
        val etiquetas = listOf(EtiquetaImagen("Lake", 0.95f))

        assertNull(DescriptorImagenes.descripcion(textos, etiquetas, ancho, alto))
    }
}
