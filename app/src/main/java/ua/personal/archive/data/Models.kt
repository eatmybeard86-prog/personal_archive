package ua.personal.archive.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Вбудовані розділи, що створюються при першому запуску. Користувач може їх перейменувати,
 * видалити й додати власні — тоді список зберігається в зашифрованому індексі ([Section]).
 * Енум лишається для сумісності зі старими записами (поле Document.category).
 */
enum class Category(val title: String, val hint: String, val icon: String, val color: Long) {
    PERSONAL("Особисті", "Паспорт, ID-картка, РНОКПП, свідоцтва", "badge", 0xFF3B82F6),
    REAL_ESTATE("Нерухомість", "Право власності, витяги з реєстру, техпаспорти, договори", "home", 0xFF38BDF8),
    VEHICLE("Транспорт", "Техпаспорт, посвідчення водія, поліс", "car", 0xFF6366F1),
    FINANCE("Фінанси", "Банківські договори, податкові декларації, кредити", "savings", 0xFFFBBF24),
    LEGAL("Юридичні", "Довіреності, заповіти, рішення суду", "gavel", 0xFFA78BFA),
    MEDICAL("Медичні", "Медкартка, висновки, щеплення", "medical", 0xFFF87171),
    INSURANCE("Страхування", "Поліси та договори страхування", "shield", 0xFF34D399),
    EDUCATION("Освіта", "Дипломи, атестати, сертифікати", "school", 0xFFF472B6),
    OTHER("Інше", "Все, що не підходить до інших розділів", "more", 0xFF94A3B8);

    fun toSection() = Section(id = name, title = title, icon = icon, color = color, hint = hint)

    companion object {
        val defaults: List<Section> get() = entries.map { it.toSection() }
    }
}

/** Розділ (папка) документів. Вбудовані мають id як назву з [Category], власні — випадковий UUID. */
@Serializable
data class Section(
    val id: String,
    val title: String,
    val icon: String = "folder",
    val color: Long = 0xFF94A3B8,
    val hint: String = "",
)

@Serializable
data class Attachment(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val addedAt: Long,
    /** Розпізнаний текст скану/фото (OCR) — для пошуку. null — ще не розпізнавали. */
    val text: String? = null,
)

@Serializable
data class Document(
    val id: String,
    val title: String,
    /** Застаріле поле (до появи власних розділів); читається лише зі старих записів. */
    val category: Category = Category.OTHER,
    /** id розділу ([Section.id]). */
    val section: String = "",
    val number: String = "",
    val note: String = "",
    /** Дата закінчення дії (мс від епохи), якщо документ має строк дії. */
    val expiresAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val attachments: List<Attachment> = emptyList(),
    /** Теги для швидкого пошуку (наприклад, «сім'я», «авто», «оригінал»). */
    val tags: List<String> = emptyList(),
    /** Позначка «Обране» — документ показується вгорі головного екрана. */
    val favorite: Boolean = false,
) {
    @Transient
    val sectionId: String = section.ifEmpty { category.name }
}

@Serializable
data class VaultIndex(
    val version: Int = 2,
    val documents: List<Document> = emptyList(),
    /** null — старий індекс без власних розділів: беруться вбудовані. */
    val sections: List<Section>? = null,
)
