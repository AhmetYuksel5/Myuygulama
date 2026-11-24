package com.yesiltemizlik.ofis

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Şimdilik boş. Açık bir erişilebilirlik servisi MIUI'de uygulamanın arka
 * planda öldürülmesini önlüyor; böylece görüşme sonu yayını kaçmıyor.
 */
class OfisErisim : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
