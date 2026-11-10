package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.voice.VoiceText

/**
 * The spoken "lista de comandos" for each screen, kept in one place so it never drifts from what works.
 * Each list has only what is relevant to that screen. The commands that work everywhere are a separate
 * list (`CommandAlternatives.GENERAL_LIST`), and the other ways of saying each command live in `CommandAlternatives`.
 */
object CommandCatalog {

    private val requestPhrases = listOf(
        "lista de comandos", "lista comandos", "que comandos", "comandos disponibles", "que puedo decir",
        "que puedo hacer", "que puedes hacer", "que sabes hacer",
    )

    fun isRequest(spoken: String): Boolean = VoiceText.normalize(spoken).let { text -> requestPhrases.let { phrases -> VoiceText.hasAny(text, phrases) } }

    /**
     * Said right after a screen introduces itself, so the person knows every screen has its own list.
     * [module] is how the screen is called aloud: "cámara", "personalización"...
     */
    fun hint(module: String): String = "Di lista de comandos de $module para escuchar lo que puedes decir."

    /** Closes every list: where to find the rest. */
    private const val MORE =
        "Di otras formas de decirlo, para conocer más maneras de pedir estas cosas, o comandos generales, " +
            "para los que sirven en toda la aplicación."

    const val HOME =
        "Puedes decir: menú, o el nombre de una opción, como cámara, documentos, actividades, chats, personalización o configuración. " +
            "Qué hay enfrente, o leer texto. Iniciar sesión. Llámate, y un nombre, para ponerme nombre. " +
            "Cerrar la aplicación. " + MORE

    const val DASHBOARD =
        "Puedes decir: menú, o el nombre de una opción, como cámara, documentos, actividades, chats, personalización o configuración. " +
            "Qué hay enfrente, o leer texto. Cerrar sesión. Cerrar la aplicación. " + MORE

    const val LOGIN =
        "En el inicio de sesión puedes decir: usuario, y luego tu usuario. Contraseña, y luego tu contraseña. " +
            "También todo junto: usuario pepe, contraseña 1234. Ingresar. Crear cuenta. Cancelar, para corregir. " +
            "Olvidé mi contraseña, si no la recuerdas. Atrás, para volver al inicio. " + MORE

    const val REGISTER =
        "Al crear tu cuenta di primero tu nombre completo, luego el usuario que quieres y luego la contraseña. " +
            "Repite, para escuchar la pregunta otra vez. Cancelar, o volver, para regresar al inicio. " +
            "Lista de comandos, para escuchar esto otra vez. Por ahora, lo que digas después de cada pregunta se toma como tu respuesta."

    const val CAMERA =
        "En la cámara puedes decir: leer texto, para leer un papel, un cartel o una etiqueta. " +
            "Qué hay enfrente, para que te describa lo que ve la cámara. Atrás, para volver al menú. " + MORE

    const val READER =
        "En el lector de texto puedes decir: toma la foto, para leer lo que tienes enfrente. " +
            "Repite, o lee otra vez, para volver a leer desde el principio. " +
            "Siguiente párrafo, o párrafo anterior, para moverte por el texto. " +
            "Pausa, para detener la lectura, y continúa, para seguir. " +
            "Otra foto, para empezar con otro papel. " +
            "Elige una foto, para leer una imagen que ya tienes en tu teléfono; tú eliges cuál y solo veo esa. " +
            "Qué hay enfrente, para cambiar a describir lo que ve la cámara. " +
            "Atrás, para volver a la cámara. " + MORE

    const val ENVIRONMENT =
        "En la descripción del entorno puedes decir: qué hay enfrente, o toma la foto, para que te describa lo que ve. " +
            "Repite, o dilo otra vez, para escuchar la última descripción. " +
            "Elige una foto, para que describa una imagen que ya tienes en tu teléfono; tú eliges cuál y solo veo esa. " +
            "Qué moneda es, para reconocer una moneda con Gemini. " +
            "Guarda la moneda, para reconocerla y sumarla a la cuenta. " +
            "Cuánto llevo, para escuchar el total de billetes y monedas. " +
            "Borra las monedas, para empezar la cuenta de monedas de cero. " +
            "Leer texto, para cambiar al lector de texto. " +
            "Atrás, para volver a la cámara. " + MORE

    const val DOCUMENTS =
        "En documentos puedes decir: el nombre de una plantilla, para elegirla. " +
            "Mientras respondes las preguntas, di tu respuesta con normalidad. " +
            "Repite la pregunta, para escucharla otra vez. Cancelar, para elegir otra plantilla. " +
            "Atrás, para volver al menú. " + MORE

    const val ACTIVITIES =
        "En actividades escuchas un sonido y dices de dónde viene: izquierda, derecha, ambos lados, cerca, lejos, " +
            "de izquierda a derecha o de derecha a izquierda, y en el nivel tres también enfrente o atrás. " +
            "En vibración dices si fue corto, largo o mixto. " +
            "Repite, para escuchar otra vez. Siguiente, para el siguiente ejercicio. " +
            "Nivel uno, nivel dos o nivel tres, para elegir el nivel. Vibración, o sonidos, para cambiar de actividad. " +
            "Volver, para regresar al menú. " + MORE

    const val CHATS =
        "En chats puedes decir: lee el último, para escuchar la conversación más reciente. " +
            "Siguiente, o anterior, para moverte entre conversaciones. Repite, para escucharla otra vez. " +
            "Cuántos chats tengo. Borra este chat, o borra todos los chats; te pido que confirmes con sí o no. " +
            "Atrás, para volver al menú. " + MORE

    const val PERSONALIZATION =
        "En personalización puedes decir: háblame de tú, o háblame de usted. " +
            "Llámame, y un nombre, para que te salude así. Llámate, y un nombre, para ponerme nombre. " +
            "Otra voz, para probar la siguiente voz, o voz anterior. " +
            "Más rápido, más lento, más grave o más agudo, para cambiar cómo hablo. " +
            "Activa o desactiva los sonidos y las vibraciones. Vibración suave, normal o fuerte. " +
            "Perfil de niño, perfil normal o perfil de persona mayor, para ajustar mi voz a tu edad. " +
            "Pantalla negra, o brillo mínimo. Prueba de voz, para escucharme. Atrás, para volver al menú. " + MORE

    const val SETTINGS =
        "En configuración puedes decir: activa los avisos, o desactiva los avisos, para que te diga cuando llega una notificación. " +
            "Da acceso a notificaciones, para abrir los ajustes de Android y que pueda leerlas. " +
            "Activa o desactiva escuchar fuera de la aplicación. " +
            "Cuánto espacio tengo, para el reporte de almacenamiento. Libera espacio, para borrar las fotos de más de un mes; te pido que confirmes con sí o no. " +
            "Limpia la caché, para borrar archivos temporales. " +
            "Activa o desactiva la inteligencia artificial en internet; apagada, nada sale de tu teléfono. " +
            "Borra mi cuenta, para eliminar tu cuenta y todo lo que guardaste; te pido que confirmes. " +
            "Activa o desactiva el modo simple. " +
            "Cerrar sesión. Atrás, para volver al menú. " + MORE

    /** Solo la ven administrador, directivo y educador. */
    const val GESTION =
        "En gestión puedes decir: cuántas personas, para saber cuántas ves. Toca a una persona para cambiarle el rol " +
            "o, si es un alumno, asignarle un educador. Atrás, para volver al menú. " + MORE
}
