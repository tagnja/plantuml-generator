package com.taogen.docs2uml.command;

import com.taogen.docs2uml.commons.constant.CommandError;
import com.taogen.docs2uml.commons.entity.CommandOption;
import com.taogen.docs2uml.commons.entity.ErrorMessage;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Taogen
 */
@Data
public class CommandHandler {
    private static final String URL_OPTION = "-u";
    private static final String URL_FULL_OPTION = "--url";
    private static final String PACKAGE_OPTION = "-p";
    private static final String PACKAGE_FULL_OPTION = "--package";
    private static final String SUB_PACKAGE_OPTION = "-s";
    private static final String SUB_PACKAGE_FULL_OPTION = "--subpackage";
    private static final String MEMBERS_OPTION = "-m";
    private static final String MEMBERS_FULL_OPTION = "--members";

    private static final Logger logger = LogManager.getLogger();

    // TODO: update docs. command -> argutments
    private String[] arguments;
    private CommandOption commandOption = new CommandOption();

    public CommandHandler() {
    }

    public CommandHandler(String[] arguments) {
        this.arguments = arguments;
    }

    /**
     * TODO: Update docs. new method
     */
    public static void showCommandUsage() {
        logger.info("Command Usage Example: java -jar docs2uml.jar -u https://example.com -p com.example");
    }

    // TODO: Recording. Lack design of algorithm.
    public ErrorMessage check() {
        if (arguments == null) {
            return CommandError.getErrorMessageByCode(com.taogen.docs2uml.commons.constant.CommandError.ERROR_CODE_MISS_PARAM);
        }
        Map<String, String> argumentsMap = new HashMap<>();
        for (int i = 0; i < arguments.length; i = i + 2) {
            argumentsMap.put(arguments[i], arguments[i + 1]);
        }
        return checkAndSetArguments(argumentsMap);
    }

    private ErrorMessage checkAndSetArguments(Map<String, String> argumentsMap) {
        String url = argumentsMap.get(URL_OPTION);
        url = url != null ? url : argumentsMap.get(URL_FULL_OPTION);
        String packageName = argumentsMap.get(PACKAGE_OPTION);
        packageName = packageName != null ? packageName : argumentsMap.get(PACKAGE_FULL_OPTION);
        String subPacakge = argumentsMap.get(SUB_PACKAGE_OPTION);
        subPacakge = subPacakge != null ? subPacakge : argumentsMap.get(SUB_PACKAGE_FULL_OPTION);
        String members = argumentsMap.get(MEMBERS_OPTION);
        members = members != null ? members : argumentsMap.get(MEMBERS_FULL_OPTION);

        if (url == null || packageName == null) {
            return CommandError.getErrorMessageByCode(CommandError.ERROR_CODE_MISS_PARAM);
        }
        if (!isLegalUrl(url)) {
            return CommandError.getErrorMessageByCode(CommandError.ERROR_CODE_PARAM_VALUE_FORMAT_ERROR);
        }
        if (subPacakge != null && !isBooleanValue(subPacakge)) {
            return CommandError.getErrorMessageByCode(CommandError.ERROR_CODE_PARAM_VALUE_FORMAT_ERROR);
        }
        if (members != null && !isBooleanValue(members)) {
            return CommandError.getErrorMessageByCode(CommandError.ERROR_CODE_PARAM_VALUE_FORMAT_ERROR);
        }

        this.commandOption.setUrl(url);
        this.commandOption.setPackageName(packageName);
        this.commandOption.setTopPackageName(packageName);
        if (subPacakge != null) {
            this.commandOption.setSubPackage(Boolean.parseBoolean(subPacakge));
        } else {
            // set subPackage default value: false
            this.commandOption.setSubPackage(false);
        }
        if (members != null) {
            this.commandOption.setMembers(Boolean.parseBoolean(members));
        } else {
            // default value
            this.commandOption.setMembers(true);
        }
        return CommandError.getErrorMessageByCode(com.taogen.docs2uml.commons.constant.CommandError.SUCCESS_CODE);
    }

    private boolean isBooleanValue(String subPacakge) {
        return "true".equals(subPacakge) || "false".equals(subPacakge);
    }

    private boolean isLegalUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }
}
