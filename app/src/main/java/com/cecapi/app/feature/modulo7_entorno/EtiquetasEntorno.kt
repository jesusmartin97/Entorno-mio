package com.cecapi.app.feature.modulo7_entorno

data class NombreEs(
    val texto: String,
    val conArticulo: String,
    val esFemenino: Boolean = false,
)

object EtiquetasEntorno {

    private val traducciones = mapOf(
        // Mobiliario / Hogar
        "chair" to NombreEs("silla", "una silla", true),
        "armchair" to NombreEs("sillón", "un sillón", false),
        "office chair" to NombreEs("silla de oficina", "una silla de oficina", true),
        "table" to NombreEs("mesa", "una mesa", true),
        "desk" to NombreEs("escritorio", "un escritorio", false),
        "coffee table" to NombreEs("mesa de centro", "una mesa de centro", true),
        "dining table" to NombreEs("mesa de comedor", "una mesa de comedor", true),
        "couch" to NombreEs("sofá", "un sofá", false),
        "sofa" to NombreEs("sofá", "un sofá", false),
        "bed" to NombreEs("cama", "una cama", true),
        "shelf" to NombreEs("estante", "un estante", false),
        "bookcase" to NombreEs("librero", "un librero", false),
        "cabinet" to NombreEs("mueble", "un mueble", false),
        "cupboard" to NombreEs("alacena", "una alacena", true),
        "wardrobe" to NombreEs("ropero", "un ropero", false),
        "door" to NombreEs("puerta", "una puerta", true),
        "window" to NombreEs("ventana", "una ventana", true),
        "lamp" to NombreEs("lámpara", "una lámpara", true),
        "lighting" to NombreEs("lámpara", "una lámpara", true),
        "mirror" to NombreEs("espejo", "un espejo", false),
        "clock" to NombreEs("reloj", "un reloj", false),
        "wall clock" to NombreEs("reloj de pared", "un reloj de pared", false),
        "pillow" to NombreEs("almohada", "una almohada", true),
        "cushion" to NombreEs("cojín", "un cojín", false),
        "curtain" to NombreEs("cortina", "una cortina", true),
        "carpet" to NombreEs("alfombra", "una alfombra", true),
        "rug" to NombreEs("tapete", "un tapete", false),

        // Electrónicos / Dispositivos
        "laptop" to NombreEs("laptop", "una laptop", true),
        "netbook" to NombreEs("laptop", "una laptop", true),
        "computer keyboard" to NombreEs("teclado", "un teclado", false),
        "keyboard" to NombreEs("teclado", "un teclado", false),
        "computer monitor" to NombreEs("monitor", "un monitor", false),
        "display device" to NombreEs("pantalla", "una pantalla", true),
        "screen" to NombreEs("pantalla", "una pantalla", true),
        "television" to NombreEs("pantalla", "una pantalla", true),
        "tv" to NombreEs("pantalla", "una pantalla", true),
        "computer" to NombreEs("computadora", "una computadora", true),
        "desktop computer" to NombreEs("computadora", "una computadora", true),
        "mouse" to NombreEs("mouse", "un mouse", false),
        "computer mouse" to NombreEs("mouse", "un mouse", false),
        "mobile phone" to NombreEs("teléfono", "un teléfono", false),
        "smartphone" to NombreEs("teléfono", "un teléfono", false),
        "telephone" to NombreEs("teléfono", "un teléfono", false),
        "cell phone" to NombreEs("teléfono celular", "un teléfono celular", false),
        "cellular network" to NombreEs("teléfono", "un teléfono", false),
        "gadget" to NombreEs("dispositivo", "un dispositivo", false),
        "communication device" to NombreEs("dispositivo", "un dispositivo", false),
        "headphones" to NombreEs("audífonos", "unos audífonos", false),
        "headset" to NombreEs("audífonos", "unos audífonos", false),
        "audio equipment" to NombreEs("equipo de audio", "un equipo de audio", false),
        "camera" to NombreEs("cámara", "una cámara", true),
        "remote control" to NombreEs("control remoto", "un control remoto", false),
        "speaker" to NombreEs("bocina", "una bocina", true),
        "loudspeaker" to NombreEs("bocina", "una bocina", true),
        "tablet" to NombreEs("tableta", "una tableta", true),
        "microphone" to NombreEs("micrófono", "un micrófono", false),
        "cable" to NombreEs("cable", "un cable", false),
        "charger" to NombreEs("cargador", "un cargador", false),

        // Objetos personales y recipientes
        "backpack" to NombreEs("mochila", "una mochila", true),
        "bag" to NombreEs("bolsa", "una bolsa", true),
        "handbag" to NombreEs("bolsa", "una bolsa", true),
        "suitcase" to NombreEs("maleta", "una maleta", true),
        "wallet" to NombreEs("cartera", "una cartera", true),
        "purse" to NombreEs("monedero", "un monedero", false),
        "bottle" to NombreEs("botella", "una botella", true),
        "water bottle" to NombreEs("botella de agua", "una botella de agua", true),
        "glass bottle" to NombreEs("botella de vidrio", "una botella de vidrio", true),
        "cup" to NombreEs("taza", "una taza", true),
        "mug" to NombreEs("taza", "una taza", true),
        "coffee cup" to NombreEs("taza de café", "una taza de café", true),
        "glass" to NombreEs("vaso", "un vaso", false),
        "drinkware" to NombreEs("vaso", "un vaso", false),
        "glasses" to NombreEs("lentes", "unos lentes", false),
        "eyewear" to NombreEs("lentes", "unos lentes", false),
        "sunglasses" to NombreEs("lentes de sol", "unos lentes de sol", false),
        // COCO solo distingue "book": en una libreta abierta evita llamarla hoja suelta.
        "book" to NombreEs("libro o cuaderno", "un libro o cuaderno", false),
        "publication" to NombreEs("libro", "un libro", false),
        "bookcover" to NombreEs("libro", "un libro", false),
        "paper" to NombreEs("papel", "un papel", false),
        "notebook" to NombreEs("cuaderno", "un cuaderno", false),
        "pen" to NombreEs("pluma", "una pluma", true),
        "pencil" to NombreEs("lápiz", "un lápiz", false),
        "writing implement" to NombreEs("pluma", "una pluma", true),
        "stationery" to NombreEs("artículo de papelería", "un artículo de papelería", false),
        "school supplies" to NombreEs("útiles escolares", "unos útiles escolares", false),
        "notepad" to NombreEs("libreta", "una libreta", true),
        "exercise book" to NombreEs("cuaderno", "un cuaderno", false),
        "sketchbook" to NombreEs("cuaderno de dibujo", "un cuaderno de dibujo", false),
        "spiral notebook" to NombreEs("cuaderno de espiral", "un cuaderno de espiral", false),
        "colored pencil" to NombreEs("lápiz de color", "un lápiz de color", false),
        "mechanical pencil" to NombreEs("portaminas", "un portaminas", false),
        "marker pen" to NombreEs("marcador", "un marcador", false),
        "highlighter" to NombreEs("marcador fluorescente", "un marcador fluorescente", false),
        "crayon" to NombreEs("crayón", "un crayón", false),
        "chalk" to NombreEs("gis", "un gis", false),
        "glue" to NombreEs("pegamento", "un pegamento", false),
        "tape" to NombreEs("cinta adhesiva", "una cinta adhesiva", true),
        "paper clip" to NombreEs("clip", "un clip", false),
        "binder" to NombreEs("carpeta de argollas", "una carpeta de argollas", true),
        "folder" to NombreEs("carpeta", "una carpeta", true),
        "clipboard" to NombreEs("portapapeles", "un portapapeles", false),
        "notebook computer" to NombreEs("laptop", "una laptop", true),
        "laptop computer" to NombreEs("laptop", "una laptop", true),
        "portable computer" to NombreEs("laptop", "una laptop", true),
        "personal computer" to NombreEs("computadora", "una computadora", true),
        "computer screen" to NombreEs("monitor", "un monitor", false),
        "computer hardware" to NombreEs("equipo de cómputo", "un equipo de cómputo", false),
        "key" to NombreEs("llave", "una llave", true),
        "keys" to NombreEs("llaves", "unas llaves", true),
        "box" to NombreEs("caja", "una caja", true),
        "carton" to NombreEs("caja de cartón", "una caja de cartón", true),
        "umbrella" to NombreEs("paraguas", "un paraguas", false),

        // Ropa y calzado
        "shirt" to NombreEs("camisa", "una camisa", true),
        "t-shirt" to NombreEs("playera", "una playera", true),
        "top" to NombreEs("prenda", "una prenda", true),
        "pants" to NombreEs("pantalón", "un pantalón", false),
        "trousers" to NombreEs("pantalón", "un pantalón", false),
        "jeans" to NombreEs("pantalón de mezclilla", "un pantalón de mezclilla", false),
        "shorts" to NombreEs("shorts", "unos shorts", false),
        "jacket" to NombreEs("chamarra", "una chamarra", true),
        "coat" to NombreEs("abrigo", "un abrigo", false),
        "sweater" to NombreEs("suéter", "un suéter", false),
        "shoe" to NombreEs("zapato", "un zapato", false),
        "shoes" to NombreEs("zapatos", "unos zapatos", false),
        "footwear" to NombreEs("zapato", "un zapato", false),
        "sneakers" to NombreEs("tenis", "unos tenis", false),
        "hat" to NombreEs("sombrero", "un sombrero", false),
        "cap" to NombreEs("gorra", "una gorra", true),
        "dress" to NombreEs("vestido", "un vestido", false),

        // Utensilios y cocina
        "plate" to NombreEs("plato", "un plato", false),
        "dish" to NombreEs("plato", "un plato", false),
        "tableware" to NombreEs("utensilio de cocina", "un utensilio de cocina", false),
        "spoon" to NombreEs("cuchara", "una cuchara", true),
        "fork" to NombreEs("tenedor", "un tenedor", false),
        "knife" to NombreEs("cuchillo", "un cuchillo", false),
        "bowl" to NombreEs("tazón", "un tazón", false),
        "pan" to NombreEs("sartén", "un sartén", false),
        "pot" to NombreEs("olla", "una olla", true),

        // Comida y plantas
        "apple" to NombreEs("manzana", "una manzana", true),
        "banana" to NombreEs("plátano", "un plátano", false),
        "orange" to NombreEs("naranja", "una naranja", true),
        "fruit" to NombreEs("fruta", "una fruta", true),
        "food" to NombreEs("alimento", "un alimento", false),
        "bread" to NombreEs("pan", "un pan", false),
        "plant" to NombreEs("planta", "una planta", true),
        "houseplant" to NombreEs("planta de interior", "una planta de interior", true),
        "flower" to NombreEs("flor", "una flor", true),
        "flowerpot" to NombreEs("maceta", "una maceta", true),
        "tree" to NombreEs("árbol", "un árbol", false),

        // Vehículos y transporte
        "car" to NombreEs("auto", "un auto", false),
        "vehicle" to NombreEs("vehículo", "un vehículo", false),
        "bicycle" to NombreEs("bicicleta", "una bicicleta", true),
        "bike" to NombreEs("bicicleta", "una bicicleta", true),
        "motorcycle" to NombreEs("motocicleta", "una motocicleta", true),
        "bus" to NombreEs("camión", "un camión", false),

        // Personas y animales (antes no estaban: una persona o mascota enfrente no se decía nada de ella)
        "person" to NombreEs("persona", "una persona", true),
        "human" to NombreEs("persona", "una persona", true),
        "man" to NombreEs("hombre", "un hombre", false),
        "woman" to NombreEs("mujer", "una mujer", true),
        "boy" to NombreEs("niño", "un niño", false),
        "girl" to NombreEs("niña", "una niña", true),
        "child" to NombreEs("niño", "un niño", false),
        "animal" to NombreEs("animal", "un animal", false),
        "dog" to NombreEs("perro", "un perro", false),
        "cat" to NombreEs("gato", "un gato", false),
        "bird" to NombreEs("pájaro", "un pájaro", false),
        "fish" to NombreEs("pez", "un pez", false),

        // Estructura del espacio (antes se ignoraban: nunca se decía si había pared, piso o escaleras)
        "wall" to NombreEs("pared", "una pared", true),
        "floor" to NombreEs("piso", "un piso", false),
        "ceiling" to NombreEs("techo", "un techo", false),
        "stairs" to NombreEs("escaleras", "unas escaleras", true),
        "room" to NombreEs("habitación", "una habitación", true),
        "picture frame" to NombreEs("marco", "un marco", false),

        // Baño
        "toilet" to NombreEs("inodoro", "un inodoro", false),
        "sink" to NombreEs("lavabo", "un lavabo", false),
        "bathtub" to NombreEs("tina", "una tina", true),
        "towel" to NombreEs("toalla", "una toalla", true),
        "soap" to NombreEs("jabón", "un jabón", false),
        "toothbrush" to NombreEs("cepillo de dientes", "un cepillo de dientes", false),

        // Electrodomésticos de cocina
        "refrigerator" to NombreEs("refrigerador", "un refrigerador", false),
        "oven" to NombreEs("horno", "un horno", false),
        "stove" to NombreEs("estufa", "una estufa", true),
        "microwave oven" to NombreEs("microondas", "un microondas", false),
        "kettle" to NombreEs("tetera", "una tetera", true),
        "toaster" to NombreEs("tostadora", "una tostadora", true),
        "blender" to NombreEs("licuadora", "una licuadora", true),
        "washing machine" to NombreEs("lavadora", "una lavadora", true),
        "dishwasher" to NombreEs("lavavajillas", "un lavavajillas", false),

        // Papelería y oficina
        "scissors" to NombreEs("tijeras", "unas tijeras", true),
        "stapler" to NombreEs("engrapadora", "una engrapadora", true),
        "calculator" to NombreEs("calculadora", "una calculadora", true),
        "ruler" to NombreEs("regla", "una regla", true),
        "eraser" to NombreEs("borrador", "un borrador", false),
        "envelope" to NombreEs("sobre", "un sobre", false),
        "whiteboard" to NombreEs("pizarrón", "un pizarrón", false),

        // Calle y exterior
        "traffic light" to NombreEs("semáforo", "un semáforo", false),
        "parking meter" to NombreEs("parquímetro", "un parquímetro", false),
        "fire hydrant" to NombreEs("hidrante", "un hidrante", false),
        "stop sign" to NombreEs("señal de alto", "una señal de alto", true),
        "airplane" to NombreEs("avión", "un avión", false),
        "train" to NombreEs("tren", "un tren", false),
        "truck" to NombreEs("camión", "un camión", false),
        "boat" to NombreEs("barco", "un barco", false),
        "street light" to NombreEs("poste de luz", "un poste de luz", false),
        "bench" to NombreEs("banca", "una banca", true),
        "trash can" to NombreEs("bote de basura", "un bote de basura", false),
        "sidewalk" to NombreEs("banqueta", "una banqueta", true),

        // Deportes y varios
        "ball" to NombreEs("pelota", "una pelota", true),
        "football" to NombreEs("balón", "un balón", false),
        "basketball" to NombreEs("balón de básquetbol", "un balón de básquetbol", false),
        "banknote" to NombreEs("billete", "un billete", false),
        "coin" to NombreEs("moneda", "una moneda", true),
        "scale" to NombreEs("báscula", "una báscula", true),
        "candle" to NombreEs("vela", "una vela", true),
        "fan" to NombreEs("ventilador", "un ventilador", false),
        "guitar" to NombreEs("guitarra", "una guitarra", true),
        "musical instrument" to NombreEs("instrumento musical", "un instrumento musical", false),

        // Otras clases concretas del modelo COCO.
        "sports ball" to NombreEs("balón", "un balón", false),
        "kite" to NombreEs("papalote", "un papalote", false),
        "baseball bat" to NombreEs("bate de béisbol", "un bate de béisbol", false),
        "baseball glove" to NombreEs("guante de béisbol", "un guante de béisbol", false),
        "skateboard" to NombreEs("patineta", "una patineta", true),
        "surfboard" to NombreEs("tabla de surf", "una tabla de surf", true),
        "tennis racket" to NombreEs("raqueta de tenis", "una raqueta de tenis", true),
        "wine glass" to NombreEs("copa", "una copa", true),
        "sandwich" to NombreEs("sándwich", "un sándwich", false),
        "broccoli" to NombreEs("brócoli", "un brócoli", false),
        "carrot" to NombreEs("zanahoria", "una zanahoria", true),
        "hot dog" to NombreEs("hot dog", "un hot dog", false),
        "pizza" to NombreEs("pizza", "una pizza", true),
        "donut" to NombreEs("dona", "una dona", true),
        "cake" to NombreEs("pastel", "un pastel", false),
        "microwave" to NombreEs("microondas", "un microondas", false),
        "hair drier" to NombreEs("secadora de cabello", "una secadora de cabello", true),
        "horse" to NombreEs("caballo", "un caballo", false),
        "sheep" to NombreEs("oveja", "una oveja", true),
        "cow" to NombreEs("vaca", "una vaca", true),
        "elephant" to NombreEs("elefante", "un elefante", false),
        "bear" to NombreEs("oso", "un oso", false),
        "zebra" to NombreEs("cebra", "una cebra", true),
        "giraffe" to NombreEs("jirafa", "una jirafa", true),

        // Etiquetas generales que también devuelve el modelo oficial de ML Kit.
        "product" to NombreEs("objeto", "un objeto", false),
        "toy" to NombreEs("juguete", "un juguete", false),
        "stuffed toy" to NombreEs("peluche", "un peluche", false),
        "plush" to NombreEs("peluche", "un peluche", false),
        "lego" to NombreEs("bloque de construcción", "un bloque de construcción", false),
        "comics" to NombreEs("cómic", "un cómic", false),
        "newspaper" to NombreEs("periódico", "un periódico", false),
        "receipt" to NombreEs("recibo", "un recibo", false),
        "passport" to NombreEs("pasaporte", "un pasaporte", false),
        "menu" to NombreEs("menú", "un menú", false),
        "poster" to NombreEs("cartel", "un cartel", false),
        "screenshot" to NombreEs("captura de pantalla", "una captura de pantalla", true),
        "web page" to NombreEs("página en una pantalla", "una página en una pantalla", true),
        "presentation" to NombreEs("presentación", "una presentación", true),
        "statue" to NombreEs("estatua", "una estatua", true),
        "aquarium" to NombreEs("acuario", "un acuario", false),
        "drawer" to NombreEs("cajón", "un cajón", false),
        "armrest" to NombreEs("descansabrazos", "un descansabrazos", false),
        "countertop" to NombreEs("cubierta de cocina", "una cubierta de cocina", true),
        "lampshade" to NombreEs("pantalla de lámpara", "una pantalla de lámpara", true),
        "placemat" to NombreEs("mantel individual", "un mantel individual", false),
        "tablecloth" to NombreEs("mantel", "un mantel", false),
        "tile" to NombreEs("azulejo", "un azulejo", false),
        "brick" to NombreEs("ladrillo", "un ladrillo", false),
        "cage" to NombreEs("jaula", "una jaula", true),
        "balloon" to NombreEs("globo", "un globo", false),
        "necklace" to NombreEs("collar", "un collar", false),
        "bracelet" to NombreEs("pulsera", "una pulsera", true),
        "bangle" to NombreEs("brazalete", "un brazalete", false),
        "jewellery" to NombreEs("joyería", "una pieza de joyería", true),
        "ring" to NombreEs("anillo", "un anillo", false),
        "tie" to NombreEs("corbata", "una corbata", true),
        "scarf" to NombreEs("bufanda", "una bufanda", true),
        "beanie" to NombreEs("gorro", "un gorro", false),
        "jersey" to NombreEs("jersey", "un jersey", false),
        "blazer" to NombreEs("saco", "un saco", false),
        "gown" to NombreEs("bata", "una bata", true),
        "tights" to NombreEs("medias", "unas medias", true),
        "swimwear" to NombreEs("traje de baño", "un traje de baño", false),
        "wetsuit" to NombreEs("traje de buceo", "un traje de buceo", false),
        "helmet" to NombreEs("casco", "un casco", false),
        "windshield" to NombreEs("parabrisas", "un parabrisas", false),
        "tire" to NombreEs("llanta", "una llanta", true),
        "wheel" to NombreEs("rueda", "una rueda", true),
        "tractor" to NombreEs("tractor", "un tractor", false),
        "airliner" to NombreEs("avión", "un avión", false),
        "aircraft" to NombreEs("aeronave", "una aeronave", true),
        "ship" to NombreEs("barco", "un barco", false),
        "sailboat" to NombreEs("velero", "un velero", false),
        "rocket" to NombreEs("cohete", "un cohete", false),
        "twig" to NombreEs("ramita", "una ramita", true),
        "branch" to NombreEs("rama", "una rama", true),
        "petal" to NombreEs("pétalo", "un pétalo", false),
        "vegetable" to NombreEs("verdura", "una verdura", true),
        "bento" to NombreEs("comida en recipiente", "una comida en recipiente", true),
        "cheeseburger" to NombreEs("hamburguesa con queso", "una hamburguesa con queso", true),
        "cookie" to NombreEs("galleta", "una galleta", true),
        "pie" to NombreEs("pay", "un pay", false),
        "juice" to NombreEs("jugo", "un jugo", false),
        "coffee" to NombreEs("café", "un café", false),
        "cappuccino" to NombreEs("capuchino", "un capuchino", false),
        "wine" to NombreEs("botella de vino", "una botella de vino", true),
        "meal" to NombreEs("comida", "una comida", true),
        "kitchen" to NombreEs("cocina", "una cocina", true),
        "bathroom" to NombreEs("baño", "un baño", false),
        "bedroom" to NombreEs("recámara", "una recámara", true),
        "building" to NombreEs("edificio", "un edificio", false),
        "bridge" to NombreEs("puente", "un puente", false),
        "park" to NombreEs("parque", "un parque", false),
        "stadium" to NombreEs("estadio", "un estadio", false),
        "factory" to NombreEs("fábrica", "una fábrica", true),
        "museum" to NombreEs("museo", "un museo", false),
        "church" to NombreEs("iglesia", "una iglesia", true),
        "school" to NombreEs("escuela", "una escuela", true),
        "class" to NombreEs("salón de clases", "un salón de clases", false),
        "graduation" to NombreEs("birrete de graduación", "un birrete de graduación", false),
        "team" to NombreEs("grupo de personas", "un grupo de personas", false),
        "crowd" to NombreEs("multitud", "una multitud", true),
        "baby" to NombreEs("bebé", "un bebé", false),
        "butterfly" to NombreEs("mariposa", "una mariposa", true),
        "insect" to NombreEs("insecto", "un insecto", false),
        "turtle" to NombreEs("tortuga", "una tortuga", true),
        "duck" to NombreEs("pato", "un pato", false),
        "penguin" to NombreEs("pingüino", "un pingüino", false),
        "crocodile" to NombreEs("cocodrilo", "un cocodrilo", false),
        "cattle" to NombreEs("ganado", "ganado", false),
        "farm" to NombreEs("granja", "una granja", true),
        "beach" to NombreEs("playa", "una playa", true),
        "mountain" to NombreEs("montaña", "una montaña", true),
        "lake" to NombreEs("lago", "un lago", false),
        "river" to NombreEs("río", "un río", false),
        "forest" to NombreEs("bosque", "un bosque", false),
        "waterfall" to NombreEs("cascada", "una cascada", true),
        "volcano" to NombreEs("volcán", "un volcán", false),
        "sky" to NombreEs("cielo", "el cielo", false),

        // Partes del cuerpo (el etiquetador de ML Kit las nombra y antes no se decían)
        "hand" to NombreEs("mano", "una mano", true),
        "finger" to NombreEs("dedo", "un dedo", false),
        "thumb" to NombreEs("pulgar", "un pulgar", false),
        "nail" to NombreEs("uña", "una uña", true),
        "arm" to NombreEs("brazo", "un brazo", false),
        "wrist" to NombreEs("muñeca", "una muñeca", true),
        "elbow" to NombreEs("codo", "un codo", false),
        "shoulder" to NombreEs("hombro", "un hombro", false),
        "leg" to NombreEs("pierna", "una pierna", true),
        "knee" to NombreEs("rodilla", "una rodilla", true),
        "foot" to NombreEs("pie", "un pie", false),
        "toe" to NombreEs("dedo del pie", "un dedo del pie", false),
        "head" to NombreEs("cabeza", "una cabeza", true),
        "face" to NombreEs("cara", "una cara", true),
        "hair" to NombreEs("cabello", "cabello", false),
        "hairstyle" to NombreEs("peinado", "un peinado", false),
        "forehead" to NombreEs("frente", "una frente", true),
        "eyebrow" to NombreEs("ceja", "una ceja", true),
        "eyelash" to NombreEs("pestañas", "unas pestañas", true),
        "eye" to NombreEs("ojo", "un ojo", false),
        "nose" to NombreEs("nariz", "una nariz", true),
        "cheek" to NombreEs("mejilla", "una mejilla", true),
        "mouth" to NombreEs("boca", "una boca", true),
        "lip" to NombreEs("labios", "unos labios", false),
        "tooth" to NombreEs("diente", "un diente", false),
        "chin" to NombreEs("barbilla", "una barbilla", true),
        "jaw" to NombreEs("mandíbula", "una mandíbula", true),
        "ear" to NombreEs("oreja", "una oreja", true),
        "neck" to NombreEs("cuello", "un cuello", false),
        "beard" to NombreEs("barba", "una barba", true),
        "moustache" to NombreEs("bigote", "un bigote", false),
        "chest" to NombreEs("pecho", "un pecho", false),
        "skin" to NombreEs("piel", "piel", true),

        // Útiles escolares y de oficina
        "pencil case" to NombreEs("estuche", "un estuche", false),
        "pencil sharpener" to NombreEs("sacapuntas", "un sacapuntas", false),
        "sharpener" to NombreEs("sacapuntas", "un sacapuntas", false),
        "ballpoint pen" to NombreEs("pluma", "una pluma", true),
        "fountain pen" to NombreEs("pluma fuente", "una pluma fuente", true),
        "marker" to NombreEs("marcador", "un marcador", false),
        "rubber" to NombreEs("goma", "una goma", true),
        "protractor" to NombreEs("transportador", "un transportador", false),
        "compass" to NombreEs("compás", "un compás", false),
        "set square" to NombreEs("escuadra", "una escuadra", true),
        "lunch box" to NombreEs("lonchera", "una lonchera", true),
        "textbook" to NombreEs("libro de texto", "un libro de texto", false),
        "page" to NombreEs("hoja", "una hoja", true),
        "paper product" to NombreEs("hoja de papel", "una hoja de papel", true),
        "sheet" to NombreEs("hoja", "una hoja", true),
        "blackboard" to NombreEs("pizarrón", "un pizarrón", false),
        "chalkboard" to NombreEs("pizarrón", "un pizarrón", false),
        "map" to NombreEs("mapa", "un mapa", false),
        "globe" to NombreEs("globo terráqueo", "un globo terráqueo", false),
        "printer" to NombreEs("impresora", "una impresora", true),
        "projector" to NombreEs("proyector", "un proyector", false),
        "usb flash drive" to NombreEs("memoria USB", "una memoria USB", true),
        "flash drive" to NombreEs("memoria USB", "una memoria USB", true),
        "blanket" to NombreEs("cobija", "una cobija", true),
        "bedding" to NombreEs("ropa de cama", "ropa de cama", true),
        "bed sheet" to NombreEs("sábana", "una sábana", true),

        // Clases del detector (COCO) que faltaban
        "potted plant" to NombreEs("planta en maceta", "una planta en maceta", true),
        "remote" to NombreEs("control remoto", "un control remoto", false),
        "vase" to NombreEs("florero", "un florero", false),
        "teddy bear" to NombreEs("oso de peluche", "un oso de peluche", false),
        "frisbee" to NombreEs("frisbee", "un frisbee", false),
        "skis" to NombreEs("esquís", "unos esquís", false),
        "snowboard" to NombreEs("tabla de nieve", "una tabla de nieve", true),
    )

    private val etiquetasIgnoradas = setOf(
        "material", "parallel", "rectangle", "font", "pattern", "brand", "line",
        "sleeve", "wood", "metal", "plastic", "indoor", "flooring",
        "technology", "electronic device", "single", "snapshot",
        "photography", "design", "circle", "square", "symmetry", "mesh", "space",
        "component", "magenta", "cyan", "gray", "white", "black", "red", "blue",
        "green", "yellow", "orange", "pink", "purple", "brown", "angle",
        "text", "number", "symbol", "shape", "triangle", "curve", "logo",
        "still life photography", "stock photography", "picture frame style",
    )

    // Etiquetas que son ciertas pero poco útiles ("dispositivo" para un teléfono, "piel" para una mano): solo se
    // dicen si no hay otra mejor en la foto.
    private val etiquetasDebiles = setOf(
        "gadget", "communication device", "cellular network", "skin", "hair", "paper product", "page", "sheet",
        "bedding", "display device", "screen", "audio equipment", "technology", "tableware", "drinkware",
        "eyewear", "footwear", "writing implement", "publication", "person", "human",
    )

    fun esDebil(etiquetaIngles: String): Boolean = etiquetaIngles.lowercase().trim() in etiquetasDebiles

    // Personas y partes del cuerpo: el color que se estima (piel, ropa, fondo) no describe a la persona.
    private val etiquetasDePersona = setOf(
        "person", "human", "man", "woman", "boy", "girl", "child", "baby", "hand", "finger", "thumb", "nail", "arm",
        "wrist", "elbow", "shoulder", "leg", "knee", "foot", "toe", "head", "face", "hair", "hairstyle", "forehead",
        "eyebrow", "eyelash", "eye", "nose", "cheek", "mouth", "lip", "tooth", "chin", "jaw", "ear", "neck", "beard",
        "moustache", "chest", "skin",
    )

    /** Si conviene decir el color de este objeto (no de personas ni partes del cuerpo). */
    fun admiteColor(etiquetaIngles: String): Boolean = etiquetaIngles.lowercase().trim() !in etiquetasDePersona

    fun traducir(etiquetaIngles: String): NombreEs? {
        val clave = etiquetaIngles.lowercase().trim()
        return traducciones[clave]
    }

    fun esIgnorada(etiquetaIngles: String): Boolean {
        val clave = etiquetaIngles.lowercase().trim()
        return clave in etiquetasIgnoradas
    }

    /** Impide presentar clases genéricas o propensas a confundir objetos como si fueran concretas. */
    fun esDemasiadoGeneral(etiquetaIngles: String): Boolean =
        etiquetaIngles.lowercase().trim() in etiquetasDemasiadoGenerales

    private val etiquetasDemasiadoGenerales = setOf(
        "food", "musical instrument", "instrument", "product", "object", "thing",
        "home goods", "household goods", "fashion goods", "furniture", "appliance",
        "electronics", "electronic device", "office supplies", "stationery",
        "indoor", "outdoor", "room", "place", "still life",
    )

    fun categoriaGruesa(categoria: String?): NombreEs? {
        if (categoria == null) return null
        return when (categoria.lowercase().trim()) {
            "home goods", "home good", "household goods" -> NombreEs("objeto del hogar", "un objeto del hogar", false)
            "fashion goods", "fashion good" -> NombreEs("objeto de vestir", "un objeto de vestir", false)
            "food" -> NombreEs("alimento", "un alimento", false)
            "plant" -> NombreEs("planta", "una planta", true)
            "place" -> NombreEs("lugar", "un lugar", false)
            else -> null
        }
    }

    fun colorConcordado(color: String, nombre: NombreEs): String? {
        if (color == "desconocido") return null
        if (!nombre.esFemenino) return color
        return when (color) {
            "negro" -> "negra"
            "blanco" -> "blanca"
            "rojo" -> "roja"
            "amarillo" -> "amarilla"
            "morado" -> "morada"
            "café" -> "café"
            "rosa" -> "rosa"
            "naranja" -> "naranja"
            "azul" -> "azul"
            "verde" -> "verde"
            "gris" -> "gris"
            else -> color
        }
    }
}
