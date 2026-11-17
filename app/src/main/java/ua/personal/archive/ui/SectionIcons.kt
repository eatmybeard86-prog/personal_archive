package ua.personal.archive.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.personal.archive.data.Section

/** Іконки, які можна вибрати для розділу (ключ зберігається в індексі). */
val SectionIcons: List<Pair<String, ImageVector>> = listOf(
    "badge" to Icons.Filled.Badge,
    "home" to Icons.Filled.Home,
    "car" to Icons.Filled.DirectionsCar,
    "savings" to Icons.Filled.Savings,
    "bank" to Icons.Filled.AccountBalance,
    "gavel" to Icons.Filled.Gavel,
    "medical" to Icons.Filled.MedicalServices,
    "shield" to Icons.Filled.VerifiedUser,
    "school" to Icons.Filled.School,
    "work" to Icons.Filled.Work,
    "business" to Icons.Filled.Business,
    "family" to Icons.Filled.ChildCare,
    "pets" to Icons.Filled.Pets,
    "flight" to Icons.Filled.Flight,
    "receipt" to Icons.Filled.Receipt,
    "key" to Icons.Filled.Key,
    "heart" to Icons.Filled.Favorite,
    "star" to Icons.Filled.Star,
    "doc" to Icons.Filled.Description,
    "folder" to Icons.Filled.Folder,
    "more" to Icons.Filled.MoreHoriz,
)

/** Кольори для розділів. */
val SectionColors: List<Long> = listOf(
    0xFF3B82F6, 0xFF38BDF8, 0xFF6366F1, 0xFFA78BFA, 0xFFF472B6, 0xFFF87171,
    0xFFFB923C, 0xFFFBBF24, 0xFFA3E635, 0xFF34D399, 0xFF2DD4BF, 0xFF94A3B8,
)

val Section.iconVector: ImageVector get() = SectionIcons.firstOrNull { it.first == icon }?.second ?: Icons.Filled.Folder
val Section.tint: Color get() = Color(color)

@Composable
fun rememberSections(): List<Section> {
    val sections by LocalApp.current.repo.sections.collectAsStateWithLifecycle()
    return sections
}
