if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    val projection = projManager.getMediaProjection(resultCode, data)
    projection.registerCallback(object : MediaProjection.Callback() {
        override fun onStop() { cleanup() }
    }, handler)
    mediaProjection = projection
} else {
    mediaProjection = projManager.getMediaProjection(resultCode, data)
}
