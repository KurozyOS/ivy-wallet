package com.ivy.wallet.domain.deprecated.logic

import androidx.compose.ui.graphics.toArgb
import arrow.core.raise.either
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.IconAsset
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.CategoryRepository
import com.ivy.legacy.utils.ioThread
import com.ivy.legacy.utils.sendToCrashlytics
import com.ivy.wallet.domain.deprecated.logic.model.CreateCategoryData
import com.ivy.wallet.domain.pure.util.nextOrderNum
import java.util.UUID
import javax.inject.Inject

class CategoryCreator @Inject constructor(
    private val categoryRepository: CategoryRepository,
) {
    
    sealed class CategoryError : Exception() {
        object BlankName : CategoryError()
        object DatabaseError : CategoryError()
        data class ValidationError(val details: String) : CategoryError()
    }

    suspend fun createCategory(
        data: CreateCategoryData,
        onRefreshUI: suspend (Category) -> Unit,
        onError: (suspend (CategoryError) -> Unit)? = null
    ) {
        val name = data.name
        if (name.isBlank()) {
            onError?.invoke(CategoryError.BlankName)
            return
        }

        try {
            val newCategory = ioThread {
                val categoryResult = either {
                    Category(
                        name = NotBlankTrimmedString.from(name.trim()).bind(),
                        color = ColorInt(data.color.toArgb()),
                        icon = data.icon?.let(IconAsset::from)?.getOrNull(),
                        orderNum = categoryRepository.findMaxOrderNum().nextOrderNum(),
                        id = CategoryId(UUID.randomUUID()),
                    )
                }

                categoryResult.fold(
                    ifLeft = { 
                        onError?.invoke(CategoryError.ValidationError(it))
                        null
                    },
                    ifRight = { category ->
                        try {
                            categoryRepository.save(category)
                            category
                        } catch (e: Exception) {
                            e.sendToCrashlytics("Failed to save new category: ${category.name.value}")
                            onError?.invoke(CategoryError.DatabaseError)
                            null
                        }
                    }
                )
            }

            newCategory?.let { onRefreshUI(it) }
        } catch (e: Exception) {
            e.sendToCrashlytics("Unexpected error in createCategory")
            onError?.invoke(CategoryError.DatabaseError)
        }
    }

    suspend fun editCategory(
        updatedCategory: Category,
        onRefreshUI: suspend (Category) -> Unit,
        onError: (suspend (CategoryError) -> Unit)? = null
    ) {
        if (updatedCategory.name.value.isBlank()) {
            onError?.invoke(CategoryError.BlankName)
            return
        }

        try {
            ioThread {
                categoryRepository.save(updatedCategory)
            }

            onRefreshUI(updatedCategory)
        } catch (e: Exception) {
            e.sendToCrashlytics("Failed to update category: ${updatedCategory.name.value}")
            onError?.invoke(CategoryError.DatabaseError)
        }
    }
}
