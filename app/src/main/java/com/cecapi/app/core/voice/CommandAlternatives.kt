package com.cecapi.app.core.voice

/**
 * The other ways of saying each command, by area, for when the person asks "otras formas de decirlo".
 * Everything listed here is accepted by the code; if a synonym is added or removed there, change it here too.
 * The app also forgives small recognizer slips (a letter off, a word split in two), so these do not have
 * to be pronounced perfectly.
 */
object CommandAlternatives {

    /** What works on every screen; read for "comandos generales" and also part of the catalog of each screen. */
    const val GENERAL_LIST =
        "En cualquier pantalla puedes decir: estado del teléfono, batería, wifi, hora, fecha, año o mes. " +
            "Sube el volumen o baja el volumen. Qué notificaciones tengo. " +
            "Pantalla negra, para ver todo en negro, y pantalla normal, para volver. Brillo mínimo, y brillo normal. " +
            "Dónde estoy, para saber en qué pantalla estás. Tutorial, o cómo funciona la cámara, para aprender a usar la aplicación. " +
            "Habla más despacio, o habla más rápido. Perfil de niño, perfil normal o perfil de persona mayor. Cómo voy, para saber tu nivel. " +
            "Dime más, para profundizar en lo último que respondí. Repite la solicitud anterior. " +
            "Silencio, para que me calle un momento. Para, para detenerme del todo. " +
            "Di otras formas de decirlo, para conocer más maneras de decir cada cosa."

    class Area(val key: String, val spokenName: String, val keywords: List<String>, val text: String)

    val areas: List<Area> = listOf(
        Area(
            key = "screen",
            spokenName = "pantalla y brillo",
            keywords = listOf("pantalla", "brillo", "negra", "negro"),
            text = "Pantalla y brillo. Pantalla negra: di pantalla negra, pantalla en negro, oscurece la pantalla o pantalla oscura. " +
                "Para volver: pantalla normal, muestra la pantalla, quita la pantalla negra, enciende la pantalla, " +
                "o mantén presionada la pantalla. " +
                "Brillo mínimo: brillo mínimo, brillo bajo, baja el brillo o brillo al mínimo. " +
                "Brillo normal: brillo normal, brillo automático, brillo máximo o sube el brillo.",
        ),
        Area(
            key = "camera",
            spokenName = "cámara",
            keywords = listOf("camara", "lector", "leer texto", "entorno", "enfrente", "foto", "fotos"),
            text = "Cámara. Leer un texto: leer texto, lee esto, lee este documento o lee el texto. " +
                "Describir lo que hay enfrente: qué hay enfrente, describe, qué ves, alrededor o entorno. " +
                "Tomar la foto: toma la foto, foto, captura, escanea o fotografía. " +
                "Repetir: repite, otra vez, de nuevo, desde el principio o empieza. " +
                "Siguiente párrafo: siguiente o adelante. Párrafo anterior: anterior. " +
                "Detener la lectura: pausa o pausar. Seguir: continúa, sigue o reanuda. " +
                "Otro papel: otra foto, nueva foto, otro documento o nuevo documento. " +
                "Usar una foto que ya tienes: elige una foto, galería, mis fotos, de mi teléfono o imagen guardada; la app solo ve la que elijas. " +
                "Volver: atrás, volver, vuelve, regresa, regresar, salir, menú o pantalla anterior.",
        ),
        Area(
            key = "chats",
            spokenName = "chats",
            keywords = listOf("chat", "chats", "conversacion", "conversaciones", "historial"),
            text = "Chats. Escuchar el más reciente: lee el último, el último, la última o más reciente. " +
                "Moverte: siguiente o más antiguo para ir hacia atrás en el tiempo, y anterior o más nuevo para volver. " +
                "Repetir: repite, otra vez o de nuevo. Cantidad: cuántos chats tengo. " +
                "Borrar uno: borra este chat, borrar este, elimina este chat o borra el actual. " +
                "Borrar todos: borra todos, elimina todos o vacía. " +
                "Confirmar: sí, claro, dale, okey, por supuesto, hazlo o adelante. " +
                "Negar: no, nunca, negativo, olvídalo, déjalo o cancela.",
        ),
        Area(
            key = "activities",
            spokenName = "actividades",
            keywords = listOf("actividad", "actividades", "ejercicio", "ejercicios", "sonidos", "vibracion", "entrenar", "nivel"),
            text = "Actividades. Cambiar de actividad: sonidos, audio u oído, y vibración, vibrar o vibrador. " +
                "Elegir nivel: nivel uno, dos o tres, o primer, segundo o tercer nivel. " +
                "Responder con sonidos: izquierda, izquierdo o lado izquierdo; derecha, derecho o lado derecho; " +
                "centro, ambos lados, los dos lados o en medio; cerca, cercano o cerquita; lejos, lejano o distante; " +
                "enfrente, al frente o adelante; atrás, detrás o a mi espalda. " +
                "Si el sonido se movió: de izquierda a derecha o hacia la derecha; de derecha a izquierda o hacia la izquierda. " +
                "Responder con vibración: corto, breve o cortito; largo o prolongado; mixto, mezcla o corto y largo. " +
                "Repetir: repite, otra vez o de nuevo. Siguiente: siguiente, próximo, sigue u otro ejercicio. " +
                "Salir: volver, terminar, regresar o menú.",
        ),
        Area(
            key = "documents",
            spokenName = "documentos",
            keywords = listOf("documento", "documentos", "solicitud", "solicitudes", "plantilla", "plantillas"),
            text = "Documentos. Elegir una plantilla: di su nombre o una palabra del título, como constancia, justificación o apoyo. " +
                "Repetir la pregunta: repite, repite la pregunta o otra vez. " +
                "Cambiar de plantilla: cancelar, cancela, reiniciar, otra plantilla o nueva plantilla. " +
                "Cuando ya está lista: repite, otra vez o lee para escucharla de nuevo. " +
                "Volver: atrás, volver, regresa, salir o menú.",
        ),
        Area(
            key = "personalization",
            spokenName = "personalización",
            keywords = listOf("personalizacion", "personalizar", "voz", "voces", "tono", "velocidad"),
            text = "Personalización. Cómo te hablo: háblame de tú o tutéame, y háblame de usted. " +
                "Tu nombre: llámame Ana, me llamo Ana, mi nombre es Ana o quiero que me llames Ana. " +
                "Mi nombre: llámate Luna, te llamas Luna o tu nombre es Luna. " +
                "Cambiar la voz: otra voz, siguiente voz, cambia la voz o cambia de voz; y voz anterior para la de antes. " +
                "Velocidad: más rápido, más veloz o acelera; más lento o más despacio. " +
                "Tono: más grave o voz grave; más agudo o voz aguda. " +
                "Sonidos y vibración: activa o desactiva los sonidos, y activa o desactiva la vibración. " +
                "Fuerza de la vibración: vibración suave, normal o fuerte. " +
                "Probar: prueba de voz, probar voz, prueba la voz o di algo.",
        ),
        Area(
            key = "settings",
            spokenName = "configuración",
            keywords = listOf("configuracion", "ajustes", "almacenamiento", "espacio", "avisos", "notificaciones", "memoria"),
            text = "Configuración. Avisos de notificaciones: activa los avisos o desactiva los avisos. " +
                "Dar acceso a las notificaciones: da acceso a notificaciones, activar, habilitar o dar permiso. " +
                "Leer notificaciones: qué notificaciones tengo, léeme la última, léelas todas, o borra las notificaciones. " +
                "Fuera de la aplicación: activa o desactiva escuchar fuera de la aplicación, fuera de la app o segundo plano. " +
                "Modo simple: activa o desactiva el modo simple. " +
                "Almacenamiento: cuánto espacio tengo, espacio libre, almacenamiento o cuánto ocupa. " +
                "Liberar: libera espacio, liberar espacio, libera memoria, limpia el espacio o borra las fotos. " +
                "Caché: limpia la caché, borra la caché, vacía la caché, libera la caché o borra los archivos temporales. " +
                "Inteligencia artificial: activa la inteligencia artificial o desactívala; empieza apagada. " +
                "Cuenta: cerrar sesión, cierra mi sesión o salir de la cuenta. " +
                "Borrar todo: borra mi cuenta, elimina mi cuenta, borra mis datos o darme de baja; pide tu contraseña y luego confirmar con sí, borrar.",
        ),
        Area(
            key = "session",
            spokenName = "sesión",
            keywords = listOf("sesion", "cuenta", "iniciar", "ingresar", "login", "registro", "contrasena", "usuario"),
            text = "Sesión. Iniciar: iniciar sesión, o di usuario y tu usuario, contraseña y tu contraseña, o todo junto. " +
                "Crear cuenta: crear cuenta, registrarme, registrar o nueva cuenta. " +
                "Corregir: cancelar. Repetir la pregunta: repite o ayuda. " +
                "Olvidé mi contraseña, o no recuerdo mi contraseña: te digo que hoy no hay recuperación automática. " +
                "Salir del inicio de sesión sin entrar: atrás, volver o salir. " +
                "Cerrar sesión: cerrar sesión, cierra mi sesión, terminar sesión, salir de mi cuenta o salir de la cuenta. " +
                "Cerrar la aplicación: cerrar la aplicación, cierra la app, salir de la aplicación, salir de aquí o cerrar todo.",
        ),
        Area(
            key = "menu",
            spokenName = "menú",
            keywords = listOf("menu", "principal", "opciones", "modulos", "abrir"),
            text = "Menú. Ver las opciones: menú, módulos u opciones. " +
                "Abrir una opción: cámara o lector; documentos, solicitudes o solicitud; actividades, ejercicios o entrenar; " +
                "chats, conversaciones o historial; personalización o personalizar; configuración o ajustes. " +
                "Atajos: qué hay enfrente, describe o entorno abre la descripción; leer texto, lee esto o lee el texto abre el lector. " +
                "Ponerme nombre: llámate, te llamas o tu nombre es, y el nombre.",
        ),
        Area(
            key = "help",
            spokenName = "ayuda y aprendizaje",
            keywords = listOf("ayuda", "tutorial", "aprender", "perfil", "perfiles", "progreso", "donde estoy", "ensename"),
            text = "Ayuda y aprendizaje. Aprender a usar la aplicación: tutorial, cómo se usa, cómo funciona la aplicación o enséñame a usar la aplicación. " +
                "Una parte en concreto: tutorial de la cámara, de actividades, de documentos, de chats, de personalización, de configuración o de la pantalla negra. " +
                "Saber dónde estás: dónde estoy, en qué pantalla estoy o qué pantalla es esta. " +
                "Tu avance: cómo voy, mi nivel, mi progreso o en qué nivel voy. " +
                "Hablar más despacio: habla más despacio, habla más lento o más despacio por favor; y más rápido: habla más rápido o hablas muy despacio. " +
                "Perfiles: perfil de niño, perfil normal y perfil de persona mayor; también modo niños, modo normal y modo persona mayor.",
        ),
        Area(
            key = "general",
            spokenName = "generales",
            keywords = listOf("general", "generales", "toda la aplicacion", "todas partes", "en todo"),
            text = "Comandos generales. Estado: estado del teléfono, cómo está mi teléfono o estado del celular. " +
                "Batería: batería, pila o cuánta carga. Red: wifi, internet, señal, cobertura o datos móviles. " +
                "Hora, fecha, qué día es, año o mes. " +
                "Buscar información, cuando algo no lo entienda: qué es, quién es, quién fue, busca, o busca en Wikipedia, " +
                "y el tema; necesita internet. " +
                "Volumen: sube el volumen, más volumen, más fuerte o aumenta el volumen; baja el volumen, menos volumen o más bajo; " +
                "volumen al máximo o mínimo; y cuánto volumen. " +
                "Más detalle: dime más, cuéntame más, explícame más, a fondo, en detalle, profundiza o investiga. " +
                "Repetir: repite, otra vez, de nuevo, lo mismo, solicitud anterior o comando anterior. " +
                "Callarme un momento: silencio, espera, un momento, un segundo o cállate. " +
                "Detenerme del todo: para, detente, alto, basta, hasta luego o adiós.",
        ),
    )

    val spokenNames: String
        get() = areas.dropLast(1).joinToString(", ") { it.spokenName } + " o " + areas.last().spokenName

    /** Everything at once, in the order of [areas]. */
    val all: String get() = areas.joinToString(" ") { it.text }
}
