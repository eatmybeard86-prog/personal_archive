package ua.personal.archive.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

enum class Category(val title: String, val hint: String, val icon: ImageVector, val color: Long) {
    PERSONAL("Особисті", "Паспорт, ID-картка, РНОКПП, свідоцтва", Icons.Filled.Badge, 0xFF3B82F6),
    REAL_ESTATE("Нерухомість", "Право власності, витяги з реєстру, техпаспорти, договори", Icons.Filled.Home, 0xFF38BDF8),
    VEHICLE("Транспорт", "Техпаспорт, посвідчення водія, поліс", Icons.Filled.DirectionsCar, 0xFF6366F1),
    FINANCE("Фінанси", "Банківські договори, податкові декларації, кредити", Icons.Filled.Savings, 0xFFFBBF24),
    LEGAL("Юридичні", "Довіреності, заповіти, рішення суду", Icons.Filled.Gavel, 0xFFA78BFA),
    MEDICAL("Медичні", "Медкартка, висновки, щеплення", Icons.Filled.MedicalServices, 0xFFF87171),
    INSURANCE("Страхування", "Поліси та договори страхування", Icons.Filled.VerifiedUser, 0xFF34D399),
    EDUCATION("Освіта", "Дипломи, атестати, сертифікати", Icons.Filled.School, 0xFFF472B6),
    OTHER("Інше", "Все, що не підходить до інших розділів", Icons.Filled.MoreHoriz, 0xFF94A3B8),
}

@Serializable
data class Attachment(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val addedAt: Long,
)

@Serializable
data class Document(
    val id: String,
    val title: String,
    val category: Category,
    val number: String = "",
    val note: String = "",
    /** Дата закінчення дії (мс від епохи), якщо документ має строк дії. */
    val expiresAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val attachments: List<Attachment> = emptyList(),
)

@Serializable
data class VaultIndex(
    val version: Int = 1,
    val documents: List<Document> = emptyList(),
)
