package com.cecapi.app.core.voice

/**
 * Plain explanations of what each part of the app is for, so anyone can learn it alone, at any age, by voice.
 * They explain what the thing is and how to start; the exact commands are in `CommandAlternatives`.
 * Written for listening: short sentences, no jargon.
 */
object HelpTopics {

    /** Read for "tutorial" or "cómo se usa la aplicación". */
    const val GENERAL =
        "Esta aplicación se maneja con la voz, y sirve para usar tu teléfono sin ver la pantalla y para entrenar tus sentidos. " +
            "Para hablar conmigo di hola, o toca la pantalla. " +
            "Si quieres que me calle un momento di silencio. Si quieres que me detenga del todo, di para. " +
            "En cualquier pantalla puedes decir dónde estoy, para saber en qué parte estás; lista de comandos, para saber qué puedes decir ahí; " +
            "y otras formas de decirlo, para conocer más maneras de pedir las cosas. " +
            "Si hablo muy rápido o muy lento, di habla más despacio, o habla más rápido. " +
            "El menú tiene: cámara, para leer textos y saber qué hay enfrente; documentos, para crear solicitudes; " +
            "actividades, para entrenar el oído y el tacto; chats, para repasar tus conversaciones; " +
            "personalización, para cambiar mi voz y cómo te hablo; y configuración. " +
            "Para conocer una parte con más detalle di, por ejemplo, tutorial de la cámara, o tutorial de actividades."

    /** Topic text by the area keys of [CommandAlternatives.areas]. */
    val byArea: Map<String, String> = mapOf(
        "camera" to
            "La cámara tiene dos usos. Con leer texto, apuntas a un papel, un cartel o una etiqueta, dices toma la foto, y te lo leo en voz alta. " +
            "Con qué hay enfrente, te cuento qué objetos ve la cámara. También puedes elegir una foto que ya tengas en tu teléfono: di elige una foto. " +
            "Mientras leo puedes decir pausa, siguiente párrafo, o repite.",
        "activities" to
            "Actividades sirve para entrenar el oído y el tacto sin necesitar a nadie. Escuchas un sonido y dices de dónde viene: izquierda, derecha, " +
            "ambos lados, cerca o lejos. Son tres niveles; subes de nivel cuando sumas puntos. " +
            "En vibración sientes un patrón y dices si fue corto, largo o mixto. Puedes decir repite, siguiente, o cómo voy para saber tu nivel.",
        "documents" to
            "Documentos te ayuda a preparar una solicitud, como una constancia. Eliges una plantilla diciendo su nombre y respondes lo que te pregunto, una cosa a la vez. " +
            "Al final te leo el documento. Si te equivocas, di cancelar y empezamos otra vez.",
        "chats" to
            "Chats guarda tus conversaciones con el asistente. Di lee el último para escuchar la más reciente, siguiente para moverte, " +
            "y borra este chat si ya no lo quieres. Siempre te pregunto antes de borrar.",
        "personalization" to
            "Personalización sirve para que la aplicación se sienta tuya. Puedes cambiar mi voz, qué tan rápido hablo, si te hablo de tú o de usted, " +
            "y el nombre con el que te saludo. Hay perfiles listos: di perfil de niño, perfil normal, o perfil de persona mayor.",
        "settings" to
            "Configuración tiene lo técnico: el volumen, si te aviso de las notificaciones, si sigo escuchando fuera de la aplicación, " +
            "cuánto espacio ocupa, la privacidad y tu cuenta. Todo lo que hagas aquí lo puedes hacer también por voz.",
        "screen" to
            "La pantalla negra apaga todo lo que se ve y deja la aplicación solo con la voz, y el brillo mínimo baja la luz. " +
            "Para volver di pantalla normal, o mantén presionada la pantalla.",
        "session" to
            "Con una cuenta se guardan tus chats y tu avance. Para entrar di usuario y tu usuario, luego contraseña y tu contraseña. " +
            "Si no tienes cuenta di crear cuenta. Puedes usar la cámara y otras partes sin cuenta, pero no se guardará nada.",
        "menu" to
            "El menú principal reúne todo: di el nombre de una parte para abrirla, por ejemplo cámara, actividades o chats. " +
            "Si no recuerdas las opciones di menú, y te las leo.",
        "help" to GENERAL,
        "general" to GENERAL,
    )
}
