package dev.latvian.mods.literalskyblock.integration;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.LevelRenderer;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class IrisCompat {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Field PIPELINE;
	private static final String[] IRIS_CLASS_NAMES = {
		"net.irisshaders.iris.Iris",
		"net.coderbot.iris.Iris"
	};
	private static final String[] IRIS_API_CLASS_NAMES = {
		"net.irisshaders.iris.api.v0.IrisApi",
		"net.coderbot.iris.api.v0.IrisApi"
	};
	private static final String[] WORLD_RENDERING_PHASE_CLASS_NAMES = {
		"net.irisshaders.iris.pipeline.WorldRenderingPhase",
		"net.coderbot.iris.pipeline.WorldRenderingPhase"
	};

	static {
		Field pipeline;
		try {
			//noinspection JavaReflectionMemberAccess
			pipeline = LevelRenderer.class.getDeclaredField("pipeline");
			pipeline.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			pipeline = null;
			LOGGER.error("Failed to get Iris pipeline field", e);
		}
		PIPELINE = pipeline;
	}

	public static boolean preRender(LevelRenderer renderer) {
		if (PIPELINE == null) {
			return false;
		}
		try {
			Class<?> irisClass = resolveClass(IRIS_CLASS_NAMES);
			if (irisClass == null) {
				LOGGER.debug("Iris class not found, skipping Iris preRender.");
				return false;
			}
			Object pipelineManager = irisClass.getMethod("getPipelineManager").invoke(null);
			Object currentDimension = irisClass.getMethod("getCurrentDimension").invoke(null);
			Method preparePipeline = pipelineManager.getClass().getMethod("preparePipeline", currentDimension.getClass());
			Object pipeline = preparePipeline.invoke(pipelineManager, currentDimension);
			if (pipeline == null) {
				return false;
			}
			PIPELINE.set(renderer, pipeline);
			//pipeline.beginLevelRendering();
			Class<?> phaseClass = resolveClass(WORLD_RENDERING_PHASE_CLASS_NAMES);
			if (phaseClass == null) {
				LOGGER.debug("Iris WorldRenderingPhase class not found, skipping Iris preRender.");
				return true;
			}
			Object nonePhase = phaseClass.getField("NONE").get(null);
			pipeline.getClass().getMethod("setOverridePhase", phaseClass).invoke(pipeline, nonePhase);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.error("Exception in preRender", e);
			return false;
		}
	}

	public static void postRender(LevelRenderer renderer) {
		if (PIPELINE == null) {
			return;
		}
		try {
			Object pipeline = PIPELINE.get(renderer);
			//pipeline.finalizeLevelRendering();
			PIPELINE.set(renderer, null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.error("Exception in postRender", e);
		}
	}

	public static boolean shadersEnabled() {
		try {
			Class<?> irisApiClass = resolveClass(IRIS_API_CLASS_NAMES);
			if (irisApiClass == null) {
				return false;
			}
			Object irisApi = irisApiClass.getMethod("getInstance").invoke(null);
			Object enabled = irisApiClass.getMethod("isShaderPackInUse").invoke(irisApi);
			return enabled instanceof Boolean && (Boolean) enabled;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Failed to query Iris shader state", e);
			return false;
		}
	}

	private static Class<?> resolveClass(String[] classNames) {
		for (String className : classNames) {
			try {
				return Class.forName(className);
			} catch (ClassNotFoundException ignored) {
				// Try next fallback.
			}
		}
		return null;
	}
}
