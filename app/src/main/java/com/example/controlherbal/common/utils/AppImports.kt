package com.example.controlherbal.common.utils

import com.example.controlherbal.common.security.*
import com.example.controlherbal.common.auth.*
import com.example.controlherbal.common.legal.*
import com.example.controlherbal.common.accessibility.*
import com.example.controlherbal.frontend.activities.main.*
import com.example.controlherbal.frontend.activities.auth.*
import com.example.controlherbal.frontend.activities.privacy.*
import com.example.controlherbal.frontend.activities.plant.*
import com.example.controlherbal.frontend.style.*

import com.example.controlherbal.database.Plant
import com.example.controlherbal.database.SensorReading
import com.example.controlherbal.domain.logic.PredictiveTheorem

/**
 * AppImports: Contenedor unificado de definiciones de tipos, alias y modelo central.
 */

typealias AppSensorReading = SensorReading
typealias AppPlant = Plant
typealias AppAnalysisResult = PredictiveTheorem.AnalysisResult

/**
 * Planta Predefinida usada en diálogos y sugerencias de UI con soporte para imágenes reales y cuidados.
 */
data class PredefinedPlantInfo(
    val name: String,
    val scientificName: String,
    val environment: String,
    val cuidados: String? = null,
    val tips: String? = null,
    val imagenUrl: String? = null
)

/**
 * Categorías predefinidas de plantas de la aplicación (Sin emojis) con datos enriquecidos y URLs de imágenes.
 */
object PlantCategoriesCatalog {

    val categoriesMap: Map<String, List<PredefinedPlantInfo>> = mapOf(
        "Flores" to listOf(
            PredefinedPlantInfo(
                "Girasol", "Helianthus annuus", "Luz",
                "Riego profundo 2 a 3 veces por semana en época de calor. Requiere suelo con excelente drenaje.",
                "Plántalo en un área despejada donde reciba al menos 6-8 horas de luz solar directa para que su tallo crezca fuerte.",
                "https://upload.wikimedia.org/wikipedia/commons/4/44/Sunflower_transparent.png"
            ),
            PredefinedPlantInfo(
                "Girasol de Sombra", "Helianthus decapetalus", "Sombra",
                "Riego moderado. Prefiere suelos húmedos pero no encharcados.",
                "Tolera la semisombra mejor que el girasol común, ideal para bordes de jardín bajo árboles.",
                "https://upload.wikimedia.org/wikipedia/commons/3/39/Helianthus_decapetalus_transparent.png"
            ),
            PredefinedPlantInfo(
                "Tulipán", "Tulipa", "Híbrido",
                "Riego escaso hasta que brote, luego moderado. Sensible al exceso de humedad en el bulbo.",
                "Para que florezcan año tras año, los bulbos necesitan un periodo de frío (invernación).",
                "https://upload.wikimedia.org/wikipedia/commons/c/c5/Tulip_transparent.png"
            ),
            PredefinedPlantInfo(
                "Rosa", "Rosa", "Luz",
                "Riego profundo 1-2 veces por semana. Poda anual al final del invierno.",
                "Evita mojar las hojas al regar para prevenir la aparición de hongos como el oídio.",
                "https://upload.wikimedia.org/wikipedia/commons/7/7b/Red_rose_transparent.png"
            ),
            PredefinedPlantInfo(
                "Lavanda", "Lavandula", "Luz",
                "Riego muy escaso una vez establecida. Soporta sequías y requiere tierra calcárea o arenosa.",
                "No la fertilices en exceso; la lavanda produce más aceites esenciales en suelos pobres.",
                "https://upload.wikimedia.org/wikipedia/commons/e/e2/Lavandula_angustifolia_transparent.png"
            ),
            PredefinedPlantInfo(
                "Caléndula", "Calendula officinalis", "Luz",
                "Riego regular, dejando secar la capa superficial del sustrato antes de volver a regar.",
                "Retira las flores marchitas continuamente para estimular una nueva y abundante floración.",
                "https://upload.wikimedia.org/wikipedia/commons/8/8f/Calendula_officinalis_transparent.png"
            ),
            PredefinedPlantInfo(
                "Orquídea", "Orchidaceae", "Sombra",
                "Riego por inmersión 1 vez a la semana. Necesita humedad ambiental alta.",
                "Sus raíces necesitan hacer fotosíntesis; plántala en macetas transparentes con corteza de pino.",
                "https://upload.wikimedia.org/wikipedia/commons/5/52/Orchid_transparent_background.png"
            )
        ),
        "Hierbas y Especias" to listOf(
            PredefinedPlantInfo(
                "Menta", "Mentha", "Sombra",
                "Riego frecuente para mantener el sustrato húmedo. Evitar encharcamiento.",
                "Plántala en maceta individual; sus raíces son invasivas y pueden ahogar otras plantas.",
                "https://upload.wikimedia.org/wikipedia/commons/4/46/Mint_leaf_transparent.png"
            ),
            PredefinedPlantInfo(
                "Albahaca", "Ocimum basilicum", "Luz",
                "Riego regular directo a la tierra. Sensible a las heladas y al frío extremo.",
                "Corta las flores en cuanto aparezcan para prolongar la vida y sabor de sus hojas.",
                "https://upload.wikimedia.org/wikipedia/commons/8/87/Basil_leaves_transparent.png"
            ),
            PredefinedPlantInfo(
                "Romero", "Salvia rosmarinus", "Luz",
                "Riego escaso. Requiere un sustrato con excelente drenaje; no tolera la humedad constante.",
                "Poda las puntas en primavera para mantener un crecimiento compacto y frondoso.",
                "https://upload.wikimedia.org/wikipedia/commons/3/30/Rosemary_transparent.png"
            ),
            PredefinedPlantInfo(
                "Tomillo", "Thymus", "Luz",
                "Riego escaso y espaciado. Adaptado a suelos secos y pobres.",
                "Cosecha justo antes de que florezca, es cuando concentra la mayor cantidad de aceites esenciales.",
                "https://upload.wikimedia.org/wikipedia/commons/1/1b/Thyme_transparent.png"
            ),
            PredefinedPlantInfo(
                "Perejil", "Petroselinum crispum", "Híbrido",
                "Riego frecuente. El sustrato debe mantenerse ligeramente húmedo de forma constante.",
                "Remoja las semillas durante 24 horas antes de sembrar para acelerar la germinación.",
                "https://upload.wikimedia.org/wikipedia/commons/4/4b/Parsley_transparent.png"
            ),
            PredefinedPlantInfo(
                "Cilantro", "Coriandrum sativum", "Híbrido",
                "Riego regular. No soporta el calor extremo prolongado.",
                "Siémbralo en tandas cada 2-3 semanas para tener cosecha continua durante toda la temporada.",
                "https://upload.wikimedia.org/wikipedia/commons/8/81/Coriander_transparent.png"
            ),
            PredefinedPlantInfo(
                "Hierbabuena", "Mentha spicata", "Híbrido",
                "Riego abundante y constante, sobre todo en meses cálidos.",
                "Pódala a ras del suelo a finales de otoño para que rebrote con fuerza en primavera.",
                "https://upload.wikimedia.org/wikipedia/commons/5/53/Spearmint_transparent.png"
            )
        ),
        "Medicinales" to listOf(
            PredefinedPlantInfo(
                "Áloe Vera", "Aloe barbadensis", "Luz",
                "Riego escaso, permitiendo que la tierra se seque completamente entre riegos.",
                "El exceso de agua pudre la raíz rápidamente. Ideal para sustrato especial de suculentas.",
                "https://upload.wikimedia.org/wikipedia/commons/9/91/Aloe_Vera_transparent.png"
            ),
            PredefinedPlantInfo(
                "Manzanilla", "Chamaemelum nobile", "Luz",
                "Riego moderado. Resiste bien la sequía una vez establecida.",
                "Cosecha las flores al mediodía en días soleados para mayor concentración de aceites esenciales.",
                "https://upload.wikimedia.org/wikipedia/commons/6/64/Chamomile_flower_transparent.png"
            ),
            PredefinedPlantInfo(
                "Diente de León", "Taraxacum officinale", "Luz",
                "Prácticamente no requiere cuidados específicos, crece en casi cualquier suelo.",
                "Todas sus partes son útiles; las hojas jóvenes se comen en ensalada y la raíz tostada sirve como infusión.",
                "https://upload.wikimedia.org/wikipedia/commons/d/df/Taraxacum_officinale_transparent.png"
            ),
            PredefinedPlantInfo(
                "Salvia", "Salvia officinalis", "Híbrido",
                "Riego moderado. Evitar mojar las hojas al regar.",
                "Poda anualmente en primavera para evitar que los tallos se vuelvan excesivamente leñosos.",
                "https://upload.wikimedia.org/wikipedia/commons/b/b3/Salvia_officinalis_transparent.png"
            ),
            PredefinedPlantInfo(
                "Eucalipto", "Eucalyptus", "Luz",
                "Riego abundante cuando es joven; tolera la sequía en etapa adulta.",
                "Requiere mucho espacio para el desarrollo de sus raíces; no es apto para cultivar cerca de construcciones.",
                "https://upload.wikimedia.org/wikipedia/commons/c/cb/Eucalyptus_leaves_transparent.png"
            ),
            PredefinedPlantInfo(
                "Ruda", "Ruta graveolens", "Híbrido",
                "Riego moderado a escaso. Tolera suelos calcáreos y rocosos.",
                "Manipula la planta con guantes durante días soleados, ya que su savia puede ser fotosensibilizante e irritar la piel.",
                "https://upload.wikimedia.org/wikipedia/commons/7/75/Ruta_graveolens_transparent.png"
            )
        ),
        "Huerto y Frutales" to listOf(
            PredefinedPlantInfo(
                "Tomate", "Solanum lycopersicum", "Luz",
                "Riego abundante y profundo. Requiere fertilización regular (potasio y fósforo).",
                "Utiliza tutores o estacas para guiar el crecimiento y evitar que los frutos toquen el suelo.",
                "https://upload.wikimedia.org/wikipedia/commons/8/89/Tomato_transparent.png"
            ),
            PredefinedPlantInfo(
                "Chile", "Capsicum", "Luz",
                "Riego constante sin encharcar. Sensible a temperaturas por debajo de los 15°C.",
                "Reduce ligeramente el riego cuando los frutos comiencen a formarse para concentrar el nivel de capsaicina (picor).",
                "https://upload.wikimedia.org/wikipedia/commons/6/6c/Chili_pepper_transparent.png"
            ),
            PredefinedPlantInfo(
                "Limón", "Citrus limon", "Luz",
                "Riego abundante en verano. Requiere un sustrato ligeramente ácido y fertilizante específico para cítricos.",
                "Poda las ramas internas en invierno para permitir que la luz y el aire entren al centro del árbol.",
                "https://upload.wikimedia.org/wikipedia/commons/e/e6/Lemon_transparent.png"
            ),
            PredefinedPlantInfo(
                "Aguacate", "Persea americana", "Luz",
                "Riego profundo pero con excelente drenaje. Sus raíces son sumamente sensibles a la asfixia por encharcamiento.",
                "Protege el tronco de la radiación solar directa cuando el árbol es joven pintándolo con cal agrícola.",
                "https://upload.wikimedia.org/wikipedia/commons/3/36/Avocado_transparent.png"
            ),
            PredefinedPlantInfo(
                "Fresa", "Fragaria", "Híbrido",
                "Riego frecuente para mantener el sustrato húmedo. Necesita suelo rico en materia orgánica.",
                "Coloca una capa de paja o mantillo sobre la tierra para evitar que las fresas se pudran al tocar el suelo húmedo.",
                "https://upload.wikimedia.org/wikipedia/commons/4/4c/Strawberry_transparent.png"
            ),
            PredefinedPlantInfo(
                "Naranjo", "Citrus sinensis", "Luz",
                "Riego regular, especialmente durante la floración y cuajado del fruto. Necesita protección contra heladas fuertes.",
                "Asegura un buen aporte de hierro en el sustrato para evitar el amarilleamiento de las hojas (clorosis).",
                "https://upload.wikimedia.org/wikipedia/commons/c/c4/Orange_fruit_transparent.png"
            )
        ),
        "Suculentas y Otros" to listOf(
            PredefinedPlantInfo(
                "Nopal", "Opuntia ficus-indica", "Luz",
                "Riego muy escaso. Tolera condiciones extremas de sol y sequía; no soporta el exceso de humedad.",
                "Para reproducirlo, corta una penca, déjala cicatrizar en la sombra por una semana y luego plántala.",
                "https://upload.wikimedia.org/wikipedia/commons/3/3f/Opuntia_ficus-indica_transparent.png"
            ),
            PredefinedPlantInfo(
                "Suculenta", "Crassulaceae", "Luz",
                "Riego por inmersión o directo solo cuando el sustrato esté 100% seco.",
                "Si las hojas se ven arrugadas, les falta agua. Si se ven translúcidas o amarillentas, tienen exceso de agua.",
                "https://upload.wikimedia.org/wikipedia/commons/5/5e/Succulent_transparent.png"
            ),
            PredefinedPlantInfo(
                "Lengua de Suegra", "Sansevieria trifasciata", "Sombra",
                "Riego quincenal o mensual. Extremadamente resistente a la falta de luz y agua.",
                "Es una excelente planta purificadora de aire para interiores; limpia toxinas como el formaldehído.",
                "https://upload.wikimedia.org/wikipedia/commons/b/be/Sansevieria_trifasciata_transparent.png"
            ),
            PredefinedPlantInfo(
                "Bambú", "Bambusoideae", "Sombra",
                "Riego abundante y constante. Prefiere ambientes con alta humedad.",
                "Si lo plantas en tierra, utiliza barreras subterráneas para controlar sus raíces, ya que puede ser muy invasivo.",
                "https://upload.wikimedia.org/wikipedia/commons/8/88/Bamboo_transparent.png"
            ),
            PredefinedPlantInfo(
                "Helecho", "Filicopsida", "Sombra",
                "Riego frecuente para mantener el sustrato húmedo pero sin encharcar. Evitar luz solar directa.",
                "Pulveriza agua sobre sus hojas regularmente, ya que los helechos prosperan en ambientes de alta humedad.",
                "https://upload.wikimedia.org/wikipedia/commons/9/90/Fern_transparent.png"
            )
        )
    )
}
