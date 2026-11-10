package com.cecapi.app.feature.modulo6_aprendizaje

/** Quién hace la actividad — para que la persona filtre por lo que tiene a la mano en ese momento. */
enum class CategoriaRecreativa(val etiqueta: String) {
    INDIVIDUAL("Sola o solo"),
    GRUPO_INVIDENTES("En grupo, con otros invidentes"),
    GRUPO_MIXTO("En grupo, con videntes u otras discapacidades"),
}

data class ActividadRecreativa(
    val titulo: String,
    val categoria: CategoriaRecreativa,
    val descripcion: String,
)

/**
 * Primer borrador de contenido para estimular y divertir, no ejercicios calificados como los de sonidos o
 * vibración — es apenas un punto de partida para ir trabajando con el Equipo 4, basado en juegos sensoriales
 * ya documentados (ONCE, educaciontrespuntocero.com) y en programas reales para jóvenes con discapacidad
 * visual como Camp Abilities (goalball, tándem, trampolín, el sistema de "buddy" con un compañero vidente).
 */
object RecreationalActivities {
    val todas: List<ActividadRecreativa> = listOf(
        ActividadRecreativa(
            titulo = "Caza el sonido",
            categoria = CategoriaRecreativa.INDIVIDUAL,
            descripcion = "Alguien esconde algo que suena (un timbre, un celular con alarma) en el cuarto. " +
                "Búscalo guiándote solo por el oído, acercándote poco a poco a donde se oye más fuerte.",
        ),
        ActividadRecreativa(
            titulo = "La caja misteriosa",
            categoria = CategoriaRecreativa.INDIVIDUAL,
            descripcion = "Mete la mano a una caja o bolsa con varios objetos de formas y texturas distintas. " +
                "Toca cada uno y trata de adivinar qué es antes de sacarlo.",
        ),
        ActividadRecreativa(
            titulo = "¿De dónde viene el sonido?",
            categoria = CategoriaRecreativa.INDIVIDUAL,
            descripcion = "Sin moverte de tu lugar, pide que alguien haga sonar una campanilla o palmadas desde " +
                "distintos puntos del cuarto. Señala hacia dónde crees que está cada vez.",
        ),
        ActividadRecreativa(
            titulo = "Memoria de sonidos cotidianos",
            categoria = CategoriaRecreativa.INDIVIDUAL,
            descripcion = "Escucha sonidos del día a día (agua cayendo, pájaros, un carro, pasos) y trata de " +
                "identificar cada uno. Puedes pedirle a alguien que te los reproduzca en desorden.",
        ),
        ActividadRecreativa(
            titulo = "Goalball",
            categoria = CategoriaRecreativa.GRUPO_INVIDENTES,
            descripcion = "El deporte paralímpico hecho para personas ciegas: una pelota con cascabeles adentro " +
                "que se juega completamente guiándose por el sonido, todos con los ojos cubiertos por igual.",
        ),
        ActividadRecreativa(
            titulo = "Ajedrez o dominó táctil",
            categoria = CategoriaRecreativa.GRUPO_INVIDENTES,
            descripcion = "Versiones en relieve de juegos de mesa clásicos, con piezas que se distinguen al tacto, " +
                "para jugar por turnos con otra persona.",
        ),
        ActividadRecreativa(
            titulo = "Tándem o trampolín guiado",
            categoria = CategoriaRecreativa.GRUPO_INVIDENTES,
            descripcion = "Actividad física con un compañero o instructor que guía por voz: bicicleta en tándem " +
                "(dos personas, un compañero vidente adelante) o trampolín con alguien marcando el ritmo.",
        ),
        ActividadRecreativa(
            titulo = "Compañero de equipo (buddy system)",
            categoria = CategoriaRecreativa.GRUPO_MIXTO,
            descripcion = "En un deporte o juego normal, un compañero vidente te ayuda a ubicarte en la cancha o " +
                "el espacio para jugar junto con todos, en vez de quedarte fuera del juego.",
        ),
        ActividadRecreativa(
            titulo = "Fútbol o tenis de mesa adaptado",
            categoria = CategoriaRecreativa.GRUPO_MIXTO,
            descripcion = "Las mismas reglas de siempre, pero con una pelota que suena, para jugar parejo con " +
                "compañeros videntes.",
        ),
        ActividadRecreativa(
            titulo = "Estaciones de juego (tipo Camp Abilities)",
            categoria = CategoriaRecreativa.GRUPO_MIXTO,
            descripcion = "Varias actividades por estaciones (trampolín, escalada, atletismo) donde cualquier " +
                "persona participa igual, tenga o no una discapacidad, y todos pasan por todas.",
        ),
    )
}
