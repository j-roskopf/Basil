package com.joetr.basil.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import com.joetr.basil.ui.components.BasilAlertDialog
import com.joetr.basil.ui.components.DialogActionButton
import com.joetr.basil.ui.components.DialogActionItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joetr.basil.domain.model.Recipe
import com.joetr.basil.domain.model.RecipeStep
import com.joetr.basil.domain.model.SessionState
import com.joetr.basil.domain.repository.ImageRepository
import com.joetr.basil.domain.usecase.ObserveRecipeUseCase
import com.joetr.basil.domain.usecase.ObserveSessionUseCase
import com.joetr.basil.domain.usecase.SaveRecipeUseCase
import com.joetr.basil.navigation.editorJsonToExtracted
import com.joetr.basil.platform.currentTimeMillis
import com.joetr.basil.platform.resizeImage
import com.joetr.basil.ui.components.CircleIconButton
import com.joetr.basil.ui.components.DetailAction
import com.joetr.basil.ui.components.HairlineDivider
import com.joetr.basil.ui.components.RecipeImage
import com.joetr.basil.ui.components.RecipeImageFullscreen
import com.joetr.basil.ui.components.SectionHeader
import com.joetr.basil.ui.components.hostFromUrl
import com.joetr.basil.ui.icons.BasilIcon
import com.joetr.basil.ui.icons.BasilIconPainter
import com.joetr.basil.ui.icons.BasilIcons
import com.joetr.basil.ui.layout.basilSafeArea
import com.joetr.basil.ui.theme.BasilRadii
import com.joetr.basil.ui.theme.BasilSpacing
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val EditorCompactBreakpoint = 1000.dp

public class EditorViewModel(
    private val saveRecipe: SaveRecipeUseCase,
    private val observeSession: ObserveSessionUseCase,
    private val observeRecipe: ObserveRecipeUseCase,
    private val imageRepository: ImageRepository,
) {
    public fun recipe(id: String) = observeRecipe(id).filterNotNull()

    public suspend fun persistPickedImage(recipeIdHint: String?, bytes: ByteArray): String {
        val resized = resizeImage(bytes, maxLongEdge = 1600, quality = 85)
        return imageRepository.saveLocalImage(recipeIdHint ?: "draft", resized)
    }

    public suspend fun loadLocalImageBytes(localImageId: String): ByteArray? =
        imageRepository.readLocalImage(localImageId)

    @OptIn(ExperimentalUuidApi::class)
    public suspend fun save(
        title: String,
        ownerId: String,
        recipeId: String?,
        description: String?,
        imageUrl: String?,
        localImageId: String?,
        sourceUrl: String?,
        servings: Int?,
        prepMinutes: Int?,
        cookMinutes: Int?,
        ingredients: List<String>,
        steps: List<RecipeStep>,
        notes: String?,
        createdAt: Long?,
    ) {
        val now = currentTimeMillis()
        saveRecipe(
            Recipe(
                id = recipeId ?: Uuid.random().toString(),
                ownerId = ownerId,
                title = title,
                description = description,
                imageUrl = imageUrl,
                localImageId = localImageId,
                sourceUrl = sourceUrl,
                servings = servings,
                prepMinutes = prepMinutes,
                cookMinutes = cookMinutes,
                ingredients = ingredients.filter { it.isNotBlank() },
                steps = steps.filter { it.text.isNotBlank() },
                notes = notes,
                createdAt = createdAt ?: now,
                updatedAt = 0L,
            ),
        )
    }

    public suspend fun currentOwnerId(): String =
        when (val session = observeSession().first()) {
            is SessionState.LocalPending -> session.deviceOwnerId
            is SessionState.Anonymous -> session.userId
            is SessionState.Authenticated -> session.userId
        }
}

@Composable
public fun EditorScreen(
    viewModel: EditorViewModel,
    recipeId: String?,
    extractedJson: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extracted = remember(extractedJson) { editorJsonToExtracted(extractedJson) }
    val loadedRecipe by if (recipeId != null) {
        viewModel.recipe(recipeId).collectAsState(initial = null)
    } else {
        remember { mutableStateOf<Recipe?>(null) }
    }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var localImageId by remember { mutableStateOf<String?>(null) }
    var previewImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var sourceUrl by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("") }
    var prepMinutes by remember { mutableStateOf("") }
    var cookMinutes by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var createdAt by remember { mutableStateOf<Long?>(null) }
    val ingredients = remember { mutableStateListOf<String>() }
    val steps = remember { mutableStateListOf<RecipeStep>() }
    var initializedFor by remember { mutableStateOf<String?>(null) }

    val initKey = recipeId ?: extractedJson
    LaunchedEffect(initKey, loadedRecipe, extracted) {
        if (initKey == initializedFor) return@LaunchedEffect
        val recipe = loadedRecipe
        when {
            recipe != null -> {
                title = recipe.title
                description = recipe.description.orEmpty()
                imageUrl = recipe.imageUrl
                localImageId = recipe.localImageId
                previewImageBytes = recipe.localImageId?.let { viewModel.loadLocalImageBytes(it) }
                sourceUrl = recipe.sourceUrl.orEmpty()
                servings = recipe.servings?.toString().orEmpty()
                prepMinutes = recipe.prepMinutes?.toString().orEmpty()
                cookMinutes = recipe.cookMinutes?.toString().orEmpty()
                notes = recipe.notes.orEmpty()
                createdAt = recipe.createdAt
                ingredients.clear()
                ingredients.addAll(recipe.ingredients.ifEmpty { listOf("") })
                steps.clear()
                steps.addAll(recipe.steps.ifEmpty { listOf(RecipeStep("")) })
                initializedFor = initKey
            }
            extracted != null -> {
                title = decodeHtmlEntities(extracted.title.orEmpty())
                description = decodeHtmlEntities(extracted.description.orEmpty())
                imageUrl = extracted.imageUrl
                localImageId = extracted.localImageId
                previewImageBytes = extracted.localImageId?.let { viewModel.loadLocalImageBytes(it) }
                sourceUrl = extracted.sourceUrl.orEmpty()
                servings = extracted.servings?.toString().orEmpty()
                prepMinutes = extracted.prepMinutes?.toString().orEmpty()
                cookMinutes = extracted.cookMinutes?.toString().orEmpty()
                ingredients.clear()
                ingredients.addAll(extracted.ingredients.ifEmpty { listOf("") })
                steps.clear()
                steps.addAll(extracted.steps.ifEmpty { listOf(RecipeStep("")) })
                initializedFor = initKey
            }
            initKey == null && ingredients.isEmpty() -> {
                ingredients.add("")
                steps.add(RecipeStep(""))
            }
        }
    }

    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showFullscreenImage by remember { mutableStateOf(false) }
    var imageError by remember { mutableStateOf<String?>(null) }
    val hasRecipeImage = previewImageBytes != null || localImageId != null || imageUrl != null
    val heroImageModel: Any? = previewImageBytes ?: localImageId?.let { "local-image://$it" }
    val imagePicker = rememberRecipeImagePicker(
        onImagePicked = { bytes ->
            scope.launch {
                runCatching { viewModel.persistPickedImage(recipeId, bytes) }
                    .onSuccess { id ->
                        localImageId = id
                        previewImageBytes = viewModel.loadLocalImageBytes(id)
                        imageUrl = null
                        imageError = null
                    }
                    .onFailure {
                        previewImageBytes = null
                        imageError = it.message ?: "Could not save image"
                    }
            }
        },
        onError = { imageError = it },
    )
    val totalMinutes = listOfNotNull(
        prepMinutes.toIntOrNull(),
        cookMinutes.toIntOrNull(),
    ).sum().takeIf { it > 0 }

    val onSaveRecipe = {
        if (!isSaving) {
            scope.launch {
                isSaving = true
                try {
                    viewModel.save(
                        title = title.ifBlank { "Untitled recipe" },
                        ownerId = viewModel.currentOwnerId(),
                        recipeId = recipeId,
                        description = description.ifBlank { null },
                        imageUrl = imageUrl,
                        localImageId = localImageId,
                        sourceUrl = sourceUrl.ifBlank { null },
                        servings = servings.toIntOrNull(),
                        prepMinutes = prepMinutes.toIntOrNull(),
                        cookMinutes = cookMinutes.toIntOrNull(),
                        ingredients = ingredients.toList(),
                        steps = steps.toList(),
                        notes = notes.ifBlank { null },
                        createdAt = createdAt,
                    )
                    onSaved()
                } finally {
                    isSaving = false
                }
            }
        }
    }

    val onHeroClick = {
        when {
            hasRecipeImage -> showFullscreenImage = true
            imagePicker.canTakePhoto && imagePicker.canPickGallery -> showImageSourceDialog = true
            imagePicker.canTakePhoto -> imagePicker.takePhoto()
            imagePicker.canPickGallery -> imagePicker.pickFromGallery()
        }
    }
    val onChangePhoto = {
        when {
            imagePicker.canTakePhoto && imagePicker.canPickGallery -> showImageSourceDialog = true
            imagePicker.canTakePhoto -> imagePicker.takePhoto()
            imagePicker.canPickGallery -> imagePicker.pickFromGallery()
        }
    }

    if (showImageSourceDialog) {
        BasilAlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = "Recipe photo",
            dismissButton = {
                DialogActionButton(
                    text = "Cancel",
                    onClick = { showImageSourceDialog = false },
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(BasilSpacing.xs)) {
                if (imagePicker.canTakePhoto) {
                    DialogActionItem(
                        text = "Take photo",
                        onClick = {
                            showImageSourceDialog = false
                            imagePicker.takePhoto()
                        },
                    )
                }
                if (imagePicker.canPickGallery) {
                    DialogActionItem(
                        text = "Choose from library",
                        onClick = {
                            showImageSourceDialog = false
                            imagePicker.pickFromGallery()
                        },
                    )
                }
                if (localImageId != null || imageUrl != null) {
                    DialogActionItem(
                        text = "Remove photo",
                        onClick = {
                            showImageSourceDialog = false
                            previewImageBytes = null
                            localImageId = null
                            imageUrl = null
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showFullscreenImage && hasRecipeImage) {
        RecipeImageFullscreen(
            title = title.ifBlank { "Recipe" },
            imageModel = heroImageModel,
            imageUrl = imageUrl,
            onDismiss = { showFullscreenImage = false },
        )
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .basilSafeArea(),
        ) {
            val compactLayout = maxWidth < EditorCompactBreakpoint
            if (compactLayout) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    EditorHeroSection(
                        title = title,
                        hasRecipeImage = hasRecipeImage,
                        heroImageModel = heroImageModel,
                        imageUrl = imageUrl,
                        onBack = onBack,
                        onHeroClick = onHeroClick,
                        onChangePhoto = onChangePhoto,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                    imageError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = BasilSpacing.xxl, vertical = BasilSpacing.sm),
                        )
                    }
                    EditorFormContent(
                        title = title,
                        onTitleChange = { title = it },
                        sourceUrl = sourceUrl,
                        servings = servings,
                        onServingsChange = { servings = it.filter { ch -> ch.isDigit() } },
                        prepMinutes = prepMinutes,
                        onPrepMinutesChange = { prepMinutes = it.filter { ch -> ch.isDigit() } },
                        cookMinutes = cookMinutes,
                        onCookMinutesChange = { cookMinutes = it.filter { ch -> ch.isDigit() } },
                        totalMinutes = totalMinutes,
                        isSaving = isSaving,
                        onSave = onSaveRecipe,
                        description = description,
                        onDescriptionChange = { description = it },
                        ingredients = ingredients,
                        steps = steps,
                        twoColumnRecipeBody = false,
                    )
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    EditorHeroSection(
                        title = title,
                        hasRecipeImage = hasRecipeImage,
                        heroImageModel = heroImageModel,
                        imageUrl = imageUrl,
                        onBack = onBack,
                        onHeroClick = onHeroClick,
                        onChangePhoto = onChangePhoto,
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight(),
                    )
                    Column(
                        Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = BasilSpacing.xxxl, vertical = BasilSpacing.xxl),
                    ) {
                        imageError?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = BasilSpacing.sm),
                            )
                        }
                        EditorFormContent(
                            title = title,
                            onTitleChange = { title = it },
                            sourceUrl = sourceUrl,
                            servings = servings,
                            onServingsChange = { servings = it.filter { ch -> ch.isDigit() } },
                            prepMinutes = prepMinutes,
                            onPrepMinutesChange = { prepMinutes = it.filter { ch -> ch.isDigit() } },
                            cookMinutes = cookMinutes,
                            onCookMinutesChange = { cookMinutes = it.filter { ch -> ch.isDigit() } },
                            totalMinutes = totalMinutes,
                            isSaving = isSaving,
                            onSave = onSaveRecipe,
                            description = description,
                            onDescriptionChange = { description = it },
                            ingredients = ingredients,
                            steps = steps,
                            twoColumnRecipeBody = true,
                        )
                    }
                }
            }
        }

        if (isSaving) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Saving…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = BasilSpacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorHeroSection(
    title: String,
    hasRecipeImage: Boolean,
    heroImageModel: Any?,
    imageUrl: String?,
    onBack: () -> Unit,
    onHeroClick: () -> Unit,
    onChangePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clickable(onClick = onHeroClick),
        contentAlignment = Alignment.Center,
    ) {
        RecipeImage(
            title = title.ifBlank { "Recipe" },
            imageUrl = imageUrl,
            imageModel = heroImageModel,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(BasilRadii.image),
        )
        if (!hasRecipeImage) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasilIcon(
                    BasilIcons.Camera,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 28.dp,
                )
                Text(
                    "Add photo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = BasilSpacing.sm),
                )
            }
        }
        CircleIconButton(
            icon = BasilIcons.Back,
            contentDescription = "Back",
            onClick = onBack,
            onImage = true,
            modifier = Modifier.align(Alignment.TopStart).padding(start = BasilSpacing.gutter, top = 18.dp),
        )
        CircleIconButton(
            icon = BasilIcons.Photo,
            contentDescription = "Change photo",
            onClick = onChangePhoto,
            onImage = true,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = BasilSpacing.gutter, top = 18.dp),
        )
    }
}

@Composable
private fun EditorFormContent(
    title: String,
    onTitleChange: (String) -> Unit,
    sourceUrl: String,
    servings: String,
    onServingsChange: (String) -> Unit,
    prepMinutes: String,
    onPrepMinutesChange: (String) -> Unit,
    cookMinutes: String,
    onCookMinutesChange: (String) -> Unit,
    totalMinutes: Int?,
    isSaving: Boolean,
    onSave: () -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    ingredients: SnapshotStateList<String>,
    steps: SnapshotStateList<RecipeStep>,
    twoColumnRecipeBody: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.then(
            if (twoColumnRecipeBody) Modifier else Modifier.padding(horizontal = BasilSpacing.xxl),
        ),
    ) {
        if (!twoColumnRecipeBody) {
            Spacer(Modifier.height(BasilSpacing.xl))
        }
        EditorTextField(
            value = title,
            onValueChange = onTitleChange,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            singleLine = false,
            minLines = 1,
        )
        Spacer(Modifier.height(BasilSpacing.md))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BasilSpacing.sm),
        ) {
            hostFromUrl(sourceUrl)?.let { host ->
                Text(host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (servings.isNotBlank()) {
                Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)
                Text("$servings servings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            totalMinutes?.let { total ->
                Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)
                Text("${total}min total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(BasilSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BasilSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorStatField(
                icon = BasilIcons.Account,
                value = servings,
                onValueChange = onServingsChange,
                label = "Servings",
                placeholder = "4",
                modifier = Modifier.weight(1f),
            )
            EditorStatField(
                icon = BasilIcons.Clock,
                value = prepMinutes,
                onValueChange = onPrepMinutesChange,
                label = "Prep",
                placeholder = "15",
                modifier = Modifier.weight(1f),
            )
            EditorStatField(
                icon = BasilIcons.Clock,
                value = cookMinutes,
                onValueChange = onCookMinutesChange,
                label = "Cook",
                placeholder = "30",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(BasilSpacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BasilSpacing.xl),
        ) {
            DetailAction(
                icon = BasilIcons.Check,
                label = if (isSaving) "Saving…" else "Save",
                onClick = onSave,
            )
        }

        Spacer(Modifier.height(BasilSpacing.lg))
        HairlineDivider()

        Spacer(Modifier.height(BasilSpacing.xl))
        EditorTextField(
            value = description,
            onValueChange = onDescriptionChange,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            singleLine = false,
            minLines = 2,
            placeholder = "Description",
        )

        Spacer(Modifier.height(BasilSpacing.lg))
        HairlineDivider()
        Spacer(Modifier.height(BasilSpacing.lg))

        if (twoColumnRecipeBody) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BasilSpacing.xxxl),
                verticalAlignment = Alignment.Top,
            ) {
                EditorIngredientsSection(
                    ingredients = ingredients,
                    modifier = Modifier.weight(1f),
                )
                EditorStepsSection(
                    steps = steps,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            EditorIngredientsSection(ingredients = ingredients)
            Spacer(Modifier.height(BasilSpacing.xl))
            EditorStepsSection(steps = steps)
        }

        Spacer(Modifier.height(BasilSpacing.xxxl))
    }
}

@Composable
private fun EditorIngredientsSection(
    ingredients: SnapshotStateList<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionHeader("Ingredients")
        Spacer(Modifier.height(BasilSpacing.sm))
        ingredients.forEachIndexed { index, value ->
            EditorTextField(
                value = value,
                onValueChange = { ingredients[index] = it },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = BasilSpacing.sm),
                placeholder = "Ingredient ${index + 1}",
            )
        }
        Text(
            "+ Add ingredient",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = BasilSpacing.sm)
                .clickable { ingredients.add("") },
        )
    }
}

@Composable
private fun EditorStepsSection(
    steps: SnapshotStateList<RecipeStep>,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionHeader("Steps")
        Spacer(Modifier.height(BasilSpacing.sm))
        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = BasilSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = BasilSpacing.sm, top = 2.dp),
                )
                EditorTextField(
                    value = step.text,
                    onValueChange = { steps[index] = step.copy(text = it) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    singleLine = false,
                    minLines = 2,
                    placeholder = "Step ${index + 1}",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            "+ Add step",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = BasilSpacing.sm)
                .clickable { steps.add(RecipeStep("")) },
        )
    }
}

@Composable
private fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    placeholder: String? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = style.copy(color = color),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = singleLine,
        minLines = minLines,
        decorationBox = { inner ->
            if (value.isEmpty() && placeholder != null) {
                Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            inner()
        },
    )
}

@Composable
private fun EditorStatField(
    icon: BasilIconPainter,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasilIcon(icon, tint = MaterialTheme.colorScheme.onSurface, size = 18.dp)
        Spacer(Modifier.width(BasilSpacing.sm))
        Column(Modifier.weight(1f, fill = false)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier.widthIn(min = 32.dp, max = 56.dp),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                },
            )
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, letterSpacing = 0.8.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private fun decodeHtmlEntities(text: String): String =
    text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
