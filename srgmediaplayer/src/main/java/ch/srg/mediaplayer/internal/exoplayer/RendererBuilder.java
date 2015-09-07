package ch.srg.mediaplayer.internal.exoplayer;

/**
 * Builds renderers for the player.
 */

public interface RendererBuilder {

	/**
	 * Constructs the necessary components for playback.
	 *
	 * @param player   The parent player.
	 * @param callback The callback to invoke with the constructed components.
	 */

	void buildRenderers(ExoPlayerDelegate player, RendererBuilderCallback callback);
}
