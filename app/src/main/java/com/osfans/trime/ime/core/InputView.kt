/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.broadcast.InputBroadcaster
import com.osfans.trime.ime.candidates.compact.CompactCandidateDelegate
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.composition.PreeditDelegate
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.symbol.LiquidWindow
import com.osfans.trime.ime.window.BoardWindowManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.allInstances
import org.kodein.di.bindInstance
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable

/**
 * Successor of the old InputRoot
 */
@SuppressLint("ViewConstructor")
class InputView(
    service: TrimeInputMethodService,
    rime: RimeSession,
    scope: ThemeScope,
) : BaseInputView(service, rime, scope),
    DIAware {
    private val keyboardBackground =
        imageView {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
    private val placeholderListener = OnClickListener { }

    private val leftPaddingSpace =
        view(::View) {
            isFocusable = false
            setOnClickListener(placeholderListener)
        }

    private val rightPaddingSpace =
        view(::View) {
            isFocusable = false
            setOnClickListener(placeholderListener)
        }

    private val bottomPaddingSpace =
        view(::View) {
            isFocusable = false
            setOnClickListener(placeholderListener)
        }

    private val updateWindowViewHeightJob: Job

    override val di = DI {
        bindInstance<InputView> { this@InputView }
        bindInstance<ContextThemeWrapper> { themedContext }
        bindInstance<ThemeScope> { scope }
        bindInstance<Theme> { scope.theme }
        bindInstance<TrimeInputMethodService> { service }
        bindInstance<RimeSession> { rime }
        bindSingleton { InputBroadcaster() }
        bindSingleton { PopupDelegate(di) }
        bindSingleton { EnterKeyDisplayDelegate(di) }
        bindSingleton { PreeditDelegate(di) }
        bindSingleton { CommonKeyboardActionListener(di) }
        bindSingleton { BoardWindowManager(di) }
        bindSingleton { InputBarDelegate(di) }
        bindSingleton { CompactCandidateDelegate(di) }
        bindSingleton { KeyboardWindow(di) }
        bindSingleton { LiquidWindow(di) }
    }

    private val broadcaster: InputBroadcaster by instance()
    private val popup: PopupDelegate by instance()
    private val enterKeyDisplay: EnterKeyDisplayDelegate by instance()
    private val preedit: PreeditDelegate by instance()
    private val windowManager: BoardWindowManager by instance()
    private val inputBar: InputBarDelegate by instance()
    private val keyboardWindow: KeyboardWindow by instance()
    private val liquidWindow: LiquidWindow by instance()

    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    private val keyboardSidePadding = theme.style.keyboardPadding
    private val keyboardSidePaddingLandscape = theme.style.keyboardPaddingLand
    private val keyboardBottomPadding = theme.style.keyboardPaddingBottom
    private val keyboardBottomPaddingLandscape = theme.style.keyboardPaddingLandBottom

    private val keyboardSidePaddingPx: Int
        get() {
            val value =
                if (context.isLandscapeMode()) keyboardSidePaddingLandscape else keyboardSidePadding
            return dp(value)
        }

    private var lastAppearanceState = Triple(false, false, false)

    private fun broadcastKeyAppearanceUpdate() {
        val composing = rime.run { statusCached.isComposing }
        val hasMenu = rime.run { hasMenu }
        val paging = rime.run { paging }
        val current = Triple(composing, hasMenu, paging)
        if (current != lastAppearanceState) {
            lastAppearanceState = current
            broadcaster.onKeyAppearanceUpdate(current.first, current.second, current.third)
        }
    }

    private val keyboardBottomPaddingPx: Int
        get() {
            val value =
                if (context.isLandscapeMode()) keyboardBottomPaddingLandscape else keyboardBottomPadding
            return dp(value)
        }

    val keyboardView: View

    /** 浮动键盘状态：为 true 时键盘脱离底部、可拖动。 */
    var floating: Boolean = false
        private set

    private var floatingScale = 1f
    private var floatingScaleY = 1f
    private var portraitContentRatio = 0f
    private var floatingMoveStartX = 0f
    private var floatingMoveStartY = 0f
    private var floatingStartTx = 0f
    private var floatingStartTy = 0f
    private var floatingResizeStartX = 0f
    private var floatingResizeStartY = 0f
    private var floatingResizeStartScale = 1f
    private val floatingLoc = IntArray(2)
    private var lastAppliedOrientation = Configuration.ORIENTATION_UNDEFINED

    /** 浮动模式下顶部拖动手柄（拖动位置）。 */
    private val floatingDragHandle =
        view(::View) {
            setBackgroundColor(scope.colors.borderColor)
            visibility = View.GONE
            setOnTouchListener { _, event -> handleFloatingMove(event) }
        }

    /** 浮动模式下右下角的橙色缩放手柄：可视圆点小、触摸区大，便于按到。 */
    private val floatingResizeHandle =
        view(::View) {
            val dot =
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFFFF9800.toInt())
                    setStroke(dp(2), android.graphics.Color.WHITE)
                }
            background = InsetDrawable(dot, dp(8))
            visibility = View.GONE
            setOnTouchListener { _, event -> handleFloatingResize(event) }
        }

    /** Restyles colors after a scheme switch without rebuilding the view tree. */
    fun refreshColors() {
        keyboardBackground.imageDrawable = scope.drawable("keyboard_background")
        popup.refreshColors()
        keyboardWindow.refreshColors()
        inputBar.refreshColors()
        preedit.refreshColors()
        windowManager.refreshColors()
    }

    init {
        // MUST call before any operation
        val receivers: List<InputBroadcastReceiver> by allInstances()
        receivers.forEach { broadcaster.addReceiver(it) }

        windowManager.cacheResidentWindow(keyboardWindow, createView = true)
        windowManager.cacheResidentWindow(liquidWindow)
        // show KeyboardWindow by default
        windowManager.attachWindow(KeyboardWindow)

        keyboardBackground.imageDrawable = scope.drawable("keyboard_background")

        keyboardView =
            constraintLayout {
                isMotionEventSplittingEnabled = true
                add(
                    keyboardBackground,
                    lParams {
                        centerInParent()
                    },
                )
                add(
                    floatingDragHandle,
                    lParams(matchParent, dp(26)) {
                        topOfParent()
                        centerHorizontally()
                    },
                )
                add(
                    inputBar.view,
                    lParams(matchParent, dp(inputBar.themedHeight)) {
                        below(floatingDragHandle)
                        centerHorizontally()
                    },
                )
                add(
                    leftPaddingSpace,
                    lParams {
                        below(inputBar.view)
                        startOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    rightPaddingSpace,
                    lParams {
                        below(inputBar.view)
                        endOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    windowManager.view,
                    lParams {
                        below(inputBar.view)
                        above(bottomPaddingSpace)
                    },
                )
                add(
                    bottomPaddingSpace,
                    lParams {
                        startToEndOf(leftPaddingSpace)
                        endToStartOf(rightPaddingSpace)
                        bottomOfParent()
                    },
                )
                add(
                    floatingResizeHandle,
                    lParams(dp(46), dp(46)) {
                        endOfParent()
                        bottomOfParent()
                    },
                )
            }

        keyboardView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (floating) {
                clampFloating()
                applyFloatingTransform()
            }
        }

        updateWindowViewHeightJob =
            service.lifecycleScope.launch {
                keyboardWindow.currentKeyboardHeight.collect {
                    windowManager.view.updateLayoutParams {
                        height = it
                    }
                }
            }

        updateKeyboardSize()

        add(
            preedit.ui.root,
            lParams(wrapContent, wrapContent) {
                above(keyboardView)
                startOfParent()
            },
        )

        add(
            keyboardView,
            lParams(matchParent, wrapContent) {
                centerHorizontally()
                bottomOfParent()
            },
        )

        add(
            popup.root,
            lParams(matchParent, matchParent) {
                centerInParent()
            },
        )
    }

    private fun updateKeyboardSize() {
        bottomPaddingSpace.updateLayoutParams {
            height = keyboardBottomPaddingPx
        }
        val sidePadding = keyboardSidePaddingPx
        val unset = LayoutParams.UNSET
        if (sidePadding == 0) {
            // hide side padding space views when unnecessary
            leftPaddingSpace.visibility = View.GONE
            rightPaddingSpace.visibility = View.GONE
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToEnd = unset
                endToStart = unset
                startOfParent()
                endOfParent()
            }
        } else {
            leftPaddingSpace.visibility = View.VISIBLE
            rightPaddingSpace.visibility = View.VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            rightPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = unset
                endToEnd = unset
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
        }
        preedit.ui.root.setPadding(sidePadding, 0, sidePadding, 0)
        inputBar.view.setPadding(sidePadding, 0, sidePadding, 0)
    }

    private fun floatingMinVisible(): Float = dp(96).toFloat()

    private fun floatingShortSide(): Float = minOf(width, height).toFloat()

    /**
     * 浮动时的键盘布局宽度。
     * 横屏时按 keyboard_height_land / keyboard_height 收窄布局宽度，
     * 让 Trime 重新排版按键，使每个按键的宽高比与竖屏一致（不再被压成棍子）。
     */
    private fun floatingTargetWidth(): Int = floatingShortSide().toInt().coerceAtLeast(dp(180))

    private fun applyFloatingWidth() {
        keyboardView.updateLayoutParams<LayoutParams> {
            width = if (floating) floatingTargetWidth() else LayoutParams.MATCH_PARENT
        }
        keyboardView.requestLayout()
    }

    private fun floatingMaxScale(): Float = 1.6f

    /** 约束浮动键盘，保证至少有一块面积留在屏幕内。 */
    private fun clampFloating() {
        val k = keyboardView
        if (k.width <= 0 || k.height <= 0) return
        val w = k.width * floatingScale
        val kh = k.height * floatingScale
        val baseTop = (height - k.height).toFloat()
        val v = floatingMinVisible()
        k.translationX = k.translationX.coerceIn(v - w, width - v)
        k.translationY = k.translationY.coerceIn(-baseTop, height - kh - baseTop)
    }

    /** 让预编辑（拼音提示）跟随浮动键盘。 */
    private fun syncFloatingOverlays() {
        if (floating) {
            preedit.ui.root.translationX = keyboardView.left + keyboardView.translationX
            preedit.ui.root.translationY = keyboardView.translationY
        } else {
            preedit.ui.root.translationX = 0f
            preedit.ui.root.translationY = 0f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleFloatingMove(event: MotionEvent): Boolean {
        if (!floating) return false
        val k = keyboardView
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                floatingMoveStartX = event.rawX
                floatingMoveStartY = event.rawY
                floatingStartTx = k.translationX
                floatingStartTy = k.translationY
                true
            }

            MotionEvent.ACTION_MOVE -> {
                k.translationX = floatingStartTx + (event.rawX - floatingMoveStartX)
                k.translationY = floatingStartTy + (event.rawY - floatingMoveStartY)
                clampFloating()
                syncFloatingOverlays()
                service.requestInsetsUpdate()
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
            else -> false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleFloatingResize(event: MotionEvent): Boolean {
        if (!floating) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                floatingResizeStartX = event.rawX
                floatingResizeStartY = event.rawY
                floatingResizeStartScale = floatingScale
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - floatingResizeStartX
                val dy = event.rawY - floatingResizeStartY
                floatingScale =
                    (floatingResizeStartScale + (dx + dy) / 2f / 600f)
                        .coerceIn(0.3f, floatingMaxScale())
                clampFloating()
                applyFloatingTransform()
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
            else -> false
        }
    }

    private fun applyFloatingTransform() {
        val k = keyboardView
        k.pivotX = 0f
        k.pivotY = 0f
        k.scaleX = floatingScale
        k.scaleY = floatingScale
        syncFloatingOverlays()
        service.requestInsetsUpdate()
    }

    /** 浮动键盘当前在屏幕上的实际矩形（含等比缩放）。 */
    fun keyboardBounds(out: Rect) {
        val k = keyboardView
        k.getLocationInWindow(floatingLoc)
        val w = (k.width * floatingScale).toInt()
        val h = (k.height * floatingScale).toInt()
        out.set(floatingLoc[0], floatingLoc[1], floatingLoc[0] + w, floatingLoc[1] + h)
    }

    fun setFloating(enabled: Boolean) {
        if (floating == enabled) return
        floating = enabled
        floatingDragHandle.visibility = if (enabled) View.VISIBLE else View.GONE
        floatingResizeHandle.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            floatingScale = 0.85f
        } else {
            floatingScale = 1f
            keyboardView.translationX = 0f
            keyboardView.translationY = 0f
        }
        applyFloatingWidth()
        keyboardView.post {
            if (floating) {
                keyboardView.translationX = floatingTargetWidth() * (1f - floatingScale) / 2f
                // 上移量受限，保证键盘上方至少留出 dp(72) 放拼音预编辑行
                val maxLift = (height - keyboardView.height - dp(72)).coerceAtLeast(0).toFloat()
                keyboardView.translationY = -minOf(height * 0.20f, maxLift)
            }
            clampFloating()
            applyFloatingTransform()
        }
    }

    /** 横屏默认以浮动形态呼出；方向变化时自动切换。 */
    private fun applyOrientationFloatingDefault() {
        val orientation = resources.configuration.orientation
        if (orientation == lastAppliedOrientation) return
        lastAppliedOrientation = orientation
        val shouldFloat = orientation == Configuration.ORIENTATION_LANDSCAPE
        keyboardView.post {
            if (shouldFloat) {
                floating = false
                setFloating(true)
            } else {
                setFloating(false)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationFloatingDefault()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            bottomMargin = getNavBarBottomInset(insets)
        }
        return insets
    }

    fun startInput(
        info: EditorInfo,
        restarting: Boolean = false,
    ) {
        updateEnterKeyLabel(info)
        broadcaster.onStartInput(info)
        if (!restarting) {
            windowManager.attachWindow(KeyboardWindow)
        }
        applyOrientationFloatingDefault()
    }

    fun updateEnterKeyLabel(info: EditorInfo) {
        enterKeyDisplay.updateLabelOnEditorInfo(info)
    }

    override fun handleRimeMessage(it: RimeMessage<*>) {
        when (it) {
            is RimeMessage.SchemaMessage -> {
                broadcaster.onRimeSchemaUpdated(it.data)

                windowManager.attachWindow(KeyboardWindow)
            }

            is RimeMessage.OptionMessage -> {
                broadcaster.onRimeOptionUpdated(it.data)

                if (it.data.option == "_liquid_keyboard") {
                    ContextCompat.getMainExecutor(service).execute {
                        windowManager.attachWindow(LiquidWindow)
                        liquidWindow.setDataByIndex(0)
                    }
                }
            }

            is RimeMessage.CompositionMessage -> {
                val data = if (candidatesMode == PopupCandidatesMode.ALWAYS_SHOW) {
                    CompositionProto()
                } else {
                    it.data
                }
                broadcaster.onCompositionUpdate(data)
            }

            is RimeMessage.BulkCandidatesMessage -> {
                broadcaster.onCandidateListUpdate(it.data)
            }

            else -> {}
        }
        broadcastKeyAppearanceUpdate()
    }

    fun updateSelection(
        start: Int,
        end: Int,
    ) {
        broadcaster.onSelectionUpdate(start, end)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean = inputBar.handleInlineSuggestions(response)

    override fun onDetachedFromWindow() {
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
        // cancel the notification job and clear all broadcast receivers,
        // implies that InputView should not be attached again after detached.
        updateWindowViewHeightJob.cancel()
        popup.root.removeAllViews()
        broadcaster.clear()
        super.onDetachedFromWindow()
    }
}
