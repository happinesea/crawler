package com.happinesea.webcrawler;

import java.util.Arrays;

import lombok.Getter;

public class Const {

	@Getter
	public static enum ProcessStatus implements PersistableEnum  {
		NONE("1"), SUCCESS("0"), FAIL("9"), PROCESSING("2");

		private final String value;

		private ProcessStatus(String value) {
			this.value = value;
		}
	}

	@Getter
	public static enum DeleteFlg implements PersistableEnum {
		ON("1"), OFF("0");

		private final String value;

		private DeleteFlg(String value) {
			this.value = value;
		}
	}

	@Getter
	public static enum ContentsType implements PersistableEnum {
		HTML("1"), Wordpress("2"), X("3"), TIKTOK("4"), YOUTUBE_SHORT("5");

		private final String value;

		private ContentsType(String value) {
			this.value = value;
		}
	}

	@Getter
	public static enum ContractType implements PersistableEnum {
		NONE("0", false),
		PARTNER("1", true),
		REPRINT("2", true),
		OTHER("9", true);

		private final String value;
		private final boolean fullContentAllowed;

		private ContractType(String value, boolean fullContentAllowed) {
			this.value = value;
			this.fullContentAllowed = fullContentAllowed;
		}

		public static boolean allowsFullContent(String value) {
			return Arrays.stream(values())
					.filter(type -> type.value.equals(value))
					.findFirst()
					.map(ContractType::isFullContentAllowed)
					.orElse(false);
		}
	}
}
