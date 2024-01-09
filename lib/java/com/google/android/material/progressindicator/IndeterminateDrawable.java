/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.material.progressindicator;

import com.google.android.material.R;

import static com.google.android.material.progressindicator.LinearProgressIndicator.INDETERMINATE_ANIMATION_TYPE_CONTIGUOUS;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import com.google.android.material.progressindicator.DrawingDelegate.ActiveIndicator;

/** This class draws the graphics for indeterminate mode. */
public final class IndeterminateDrawable<S extends BaseProgressIndicatorSpec>
    extends DrawableWithAnimatedVisibilityChange {

  // Drawing delegate object.
  private DrawingDelegate<S> drawingDelegate;
  // Animator delegate object.
  private IndeterminateAnimatorDelegate<ObjectAnimator> animatorDelegate;

  private Drawable staticDummyDrawable;
  @Px int initialIndicatorTrackGapSize;
  @Px int initialTrackStopIndicatorSize;

  IndeterminateDrawable(
      @NonNull Context context,
      @NonNull BaseProgressIndicatorSpec baseSpec,
      @NonNull DrawingDelegate<S> drawingDelegate,
      @NonNull IndeterminateAnimatorDelegate<ObjectAnimator> animatorDelegate) {
    super(context, baseSpec);

    initialIndicatorTrackGapSize = baseSpec.indicatorTrackGapSize;
    if (baseSpec instanceof LinearProgressIndicatorSpec) {
      initialTrackStopIndicatorSize =
          ((LinearProgressIndicatorSpec) baseSpec).trackStopIndicatorSize;
    }
    setDrawingDelegate(drawingDelegate);
    setAnimatorDelegate(animatorDelegate);
  }

  /**
   * Creates an instance of {@link IndeterminateDrawable} for {@link LinearProgressIndicator} with
   * {@link LinearProgressIndicatorSpec}.
   *
   * @param context The current context.
   * @param spec The spec for the linear indicator.
   */
  @NonNull
  public static IndeterminateDrawable<LinearProgressIndicatorSpec> createLinearDrawable(
      @NonNull Context context, @NonNull LinearProgressIndicatorSpec spec) {
    return createLinearDrawable(context, spec, new LinearDrawingDelegate(spec));
  }

  /**
   * Creates an instance of {@link IndeterminateDrawable} for {@link LinearProgressIndicator} with
   * {@link LinearProgressIndicatorSpec}.
   *
   * @param context The current context.
   * @param spec The spec for the linear indicator.
   * @param drawingDelegate The LinearDrawingDelegate object.
   */
  @NonNull
  static IndeterminateDrawable<LinearProgressIndicatorSpec> createLinearDrawable(
      @NonNull Context context,
      @NonNull LinearProgressIndicatorSpec spec,
      @NonNull LinearDrawingDelegate drawingDelegate) {
    return new IndeterminateDrawable<>(
        context,
        /* baseSpec= */ spec,
        drawingDelegate,
        spec.indeterminateAnimationType == INDETERMINATE_ANIMATION_TYPE_CONTIGUOUS
            ? new LinearIndeterminateContiguousAnimatorDelegate(spec)
            : new LinearIndeterminateDisjointAnimatorDelegate(context, spec));
  }

  /**
   * Creates an instance of {@link IndeterminateDrawable} for {@link CircularProgressIndicator} with
   * {@link CircularProgressIndicatorSpec}.
   *
   * @param context The current context.
   * @param spec The spec for the circular indicator.
   */
  @NonNull
  public static IndeterminateDrawable<CircularProgressIndicatorSpec> createCircularDrawable(
      @NonNull Context context, @NonNull CircularProgressIndicatorSpec spec) {
    return createCircularDrawable(context, spec, new CircularDrawingDelegate(spec));
  }

  /**
   * Creates an instance of {@link IndeterminateDrawable} for {@link CircularProgressIndicator} with
   * {@link CircularProgressIndicatorSpec}.
   *
   * @param context The current context.
   * @param spec The spec for the circular indicator.
   * @param drawingDelegate The CircularDrawingDelegate object.
   */
  @NonNull
  static IndeterminateDrawable<CircularProgressIndicatorSpec> createCircularDrawable(
      @NonNull Context context,
      @NonNull CircularProgressIndicatorSpec spec,
      @NonNull CircularDrawingDelegate drawingDelegate) {
    IndeterminateDrawable<CircularProgressIndicatorSpec> indeterminateDrawable =
        new IndeterminateDrawable<>(
            context,
            /* baseSpec= */ spec,
            drawingDelegate,
            new CircularIndeterminateAnimatorDelegate(spec));
    indeterminateDrawable.setStaticDummyDrawable(
        VectorDrawableCompat.create(context.getResources(), R.drawable.indeterminate_static, null));
    return indeterminateDrawable;
  }

  // ******************* Overridden methods *******************

  /**
   * Sets the visibility of this drawable. It calls the {@link
   * DrawableWithAnimatedVisibilityChange#setVisible(boolean, boolean, boolean)} to start the
   * show/hide animation properly. The indeterminate animation will be started if animation is
   * requested.
   *
   * @param visible Whether to make the drawable visible.
   * @param restart Whether to force starting the animation from the beginning.
   * @param animate Whether to change the visibility with animation.
   * @return {@code true}, if the visibility changes or will change after the animation; {@code
   *     false}, otherwise.
   */
  @Override
  boolean setVisibleInternal(boolean visible, boolean restart, boolean animate) {
    boolean changed = super.setVisibleInternal(visible, restart, animate);

    if (isSystemAnimatorDisabled() && staticDummyDrawable != null) {
      return staticDummyDrawable.setVisible(visible, restart);
    }

    // Unless it's showing or hiding, cancels the main animator.
    if (!isRunning()) {
      animatorDelegate.cancelAnimatorImmediately();
    }
    // Restarts the main animator if it's visible and needs to be animated.
    if (visible
        && (animate
            || (VERSION.SDK_INT <= VERSION_CODES.LOLLIPOP_MR1 && !isSystemAnimatorDisabled()))) {
      animatorDelegate.startAnimator();
    }

    return changed;
  }

  @Override
  public int getIntrinsicWidth() {
    return drawingDelegate.getPreferredWidth();
  }

  @Override
  public int getIntrinsicHeight() {
    return drawingDelegate.getPreferredHeight();
  }

  // ******************* Drawing methods *******************

  /** Draws the graphics based on the progress indicator's properties and the animation states. */
  @Override
  public void draw(@NonNull Canvas canvas) {
    Rect clipBounds = new Rect();

    if (getBounds().isEmpty() || !isVisible() || !canvas.getClipBounds(clipBounds)) {
      // Escape if bounds are empty, clip bounds are empty, or currently hidden.
      return;
    }

    if (isSystemAnimatorDisabled() && staticDummyDrawable != null) {
      staticDummyDrawable.setBounds(getBounds());
      Log.d("ProgressIndicator", "bounds: " + staticDummyDrawable.getBounds());
      DrawableCompat.setTint(staticDummyDrawable, baseSpec.indicatorColors[0]);
      staticDummyDrawable.draw(canvas);
      return;
    }

    canvas.save();
    drawingDelegate.validateSpecAndAdjustCanvas(
        canvas, getBounds(), getGrowFraction(), isShowing(), isHiding());

    if (initialIndicatorTrackGapSize > 0) {
      if (drawingDelegate instanceof LinearDrawingDelegate) {
        ((LinearProgressIndicatorSpec) drawingDelegate.spec).trackStopIndicatorSize = 0;
      } else if (drawingDelegate instanceof CircularDrawingDelegate) {
        baseSpec.indicatorTrackGapSize = 0;
      }

      // Draws the transparent track.
      int trackColor = baseSpec.trackColor;
      baseSpec.trackColor = Color.TRANSPARENT;
      drawingDelegate.fillTrack(canvas, paint, getAlpha());
      baseSpec.trackColor = trackColor;
    } else {
      drawingDelegate.fillTrack(canvas, paint, getAlpha());
    }

    for (int indicatorIndex = 0;
        indicatorIndex < animatorDelegate.activeIndicators.size();
        indicatorIndex++) {

      // Draws the actual indicators.
      drawingDelegate.fillIndicator(
          canvas, paint, animatorDelegate.activeIndicators.get(indicatorIndex), getAlpha());

      if (drawingDelegate instanceof LinearDrawingDelegate && baseSpec.indicatorTrackGapSize > 0) {
        // Draws the track using fake indicators around the current indicator.
        drawTrackIndicators(canvas, indicatorIndex);
      }
    }

    canvas.restore();
  }

  private void drawTrackIndicators(@NonNull Canvas canvas, int indicatorIndex) {
    float previousIndicatorEndFraction =
        indicatorIndex == 0
            ? 0f
            : animatorDelegate.activeIndicators.get(indicatorIndex - 1).endFraction;
    // Draws the fake indicators as the track to the left of the current indicator.
    ActiveIndicator track = new ActiveIndicator();
    track.startFraction = previousIndicatorEndFraction;
    track.endFraction = animatorDelegate.activeIndicators.get(indicatorIndex).startFraction;
    track.color = baseSpec.trackColor;
    drawingDelegate.fillIndicator(canvas, paint, track, getAlpha());
    if (indicatorIndex == animatorDelegate.activeIndicators.size() - 1) {
      // Draws the fake indicator as the track to the right of the last indicator.
      track.startFraction = animatorDelegate.activeIndicators.get(indicatorIndex).endFraction;
      track.endFraction = 1f;
      track.color = baseSpec.trackColor;
      drawingDelegate.fillIndicator(canvas, paint, track, getAlpha());
    }
  }

  // ******************* Utility functions *******************

  private boolean isSystemAnimatorDisabled() {
    if (animatorDurationScaleProvider != null) {
      float systemAnimatorDurationScale =
          animatorDurationScaleProvider.getSystemAnimatorDurationScale(
              context.getContentResolver());
      return systemAnimatorDurationScale == 0;
    }
    return false;
  }

  // ******************* Setter and getter *******************

  /** @hide */
  @RestrictTo(Scope.LIBRARY_GROUP)
  @Nullable
  public Drawable getStaticDummyDrawable() {
    return staticDummyDrawable;
  }

  /** @hide */
  @RestrictTo(Scope.LIBRARY_GROUP)
  @VisibleForTesting
  public void setStaticDummyDrawable(@Nullable Drawable staticDummyDrawable) {
    this.staticDummyDrawable = staticDummyDrawable;
  }

  @NonNull
  IndeterminateAnimatorDelegate<ObjectAnimator> getAnimatorDelegate() {
    return animatorDelegate;
  }

  void setAnimatorDelegate(
      @NonNull IndeterminateAnimatorDelegate<ObjectAnimator> animatorDelegate) {
    this.animatorDelegate = animatorDelegate;
    animatorDelegate.registerDrawable(this);
  }

  @NonNull
  DrawingDelegate<S> getDrawingDelegate() {
    return drawingDelegate;
  }

  void setDrawingDelegate(@NonNull DrawingDelegate<S> drawingDelegate) {
    this.drawingDelegate = drawingDelegate;
  }

  void setInitialIndicatorTrackGapSize(@Px int initialIndicatorTrackGapSize) {
    this.initialIndicatorTrackGapSize = initialIndicatorTrackGapSize;
  }

  public void setInitialTrackStopIndicatorSize(int initialTrackStopIndicatorSize) {
    this.initialTrackStopIndicatorSize = initialTrackStopIndicatorSize;
  }
}
