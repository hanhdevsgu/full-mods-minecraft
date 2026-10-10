/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.command;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.command.argument.ICommandArgument;
import baritone.api.command.exception.CommandNotEnoughArgumentsException;
import baritone.api.command.exception.CommandNotFoundException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.command.manager.ICommandManager;
import baritone.api.event.events.ChatEvent;
import baritone.api.event.events.TabCompleteEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.utils.Helper;
import baritone.api.utils.SettingsUtil;
import baritone.behavior.Behavior;
import baritone.command.argument.ArgConsumer;
import baritone.command.argument.CommandArguments;
import baritone.command.manager.CommandManager;
import baritone.utils.accessor.IGuiScreen;
import net.minecraft.util.Tuple;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class ExampleBaritoneControl extends Behavior implements Helper {

    private static final Settings settings = BaritoneAPI.getSettings();
    private final ICommandManager manager;

    private static long worldJoinTime = System.currentTimeMillis();
    private static long lastServerCommandTime = 0;
    private static final Queue<String> queuedServerCommands = new ConcurrentLinkedQueue<>();
    private static boolean isDispatchingThrottledCommand = false;

    public ExampleBaritoneControl(Baritone baritone) {
        super(baritone);
        this.manager = baritone.getCommandManager();
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        worldJoinTime = System.currentTimeMillis();
        queuedServerCommands.clear();
        lastServerCommandTime = 0;
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            return;
        }
        if (ctx.player() == null || ctx.world() == null) {
            return;
        }
        if (ctx.minecraft().isSingleplayer()) {
            return;
        }

        if (!queuedServerCommands.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - worldJoinTime < 3500) {
                return;
            }
            if (now - lastServerCommandTime < 1500) {
                return;
            }

            String nextCmd = queuedServerCommands.poll();
            if (nextCmd != null) {
                isDispatchingThrottledCommand = true;
                try {
                    lastServerCommandTime = now;
                    ctx.player().sendChatMessage(nextCmd);
                } finally {
                    isDispatchingThrottledCommand = false;
                }
            }
        }
    }

    @Override
    public void onSendChatMessage(ChatEvent event) {
        if (isDispatchingThrottledCommand) {
            return;
        }

        String msg = event.getMessage();
        if (msg == null) {
            return;
        }
        String trimmed = msg.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        String prefix = settings.prefix.value;
        boolean forceRun = trimmed.startsWith(FORCE_COMMAND_PREFIX);

        // 1. Check prefixes: FORCE_COMMAND_PREFIX, settings.prefix, '.', ',', '#'
        String matchedPrefix = null;
        if (forceRun) {
            matchedPrefix = FORCE_COMMAND_PREFIX;
        } else if (settings.prefixControl.value && trimmed.startsWith(prefix)) {
            matchedPrefix = prefix;
        } else if (trimmed.startsWith(".")) {
            matchedPrefix = ".";
        } else if (trimmed.startsWith(",")) {
            matchedPrefix = ",";
        } else if (trimmed.startsWith("#")) {
            matchedPrefix = "#";
        }

        if (matchedPrefix != null) {
            event.cancel();
            String commandStr = trimmed.substring(matchedPrefix.length()).trim();
            if (!runCommand(commandStr) && !commandStr.isEmpty()) {
                new CommandNotFoundException(CommandManager.expand(commandStr).getFirst()).handle(null, null);
            }
            return;
        }

        // 2. Check if user typed "/#...", "/....", or "/b ...", "/baritone ..."
        if (trimmed.startsWith("/#") || trimmed.startsWith("/.")) {
            event.cancel();
            String commandStr = trimmed.substring(2).trim();
            runCommand(commandStr);
            return;
        }
        if (trimmed.toLowerCase(Locale.US).startsWith("/b ") || trimmed.toLowerCase(Locale.US).startsWith("/baritone ")) {
            event.cancel();
            String commandStr = trimmed.substring(trimmed.indexOf(' ') + 1).trim();
            runCommand(commandStr);
            return;
        }

        // 3. Check if user typed a Baritone command directly without prefix (e.g. "sel 1", "sel 2", "sel fill dirt", "sel ca", "stop")
        Tuple<String, List<ICommandArgument>> pair = CommandManager.expand(trimmed);
        String firstWord = pair.getFirst().toLowerCase(Locale.US);
        if (this.manager.getCommand(firstWord) != null || settings.byLowerName.containsKey(firstWord)) {
            event.cancel();
            runCommand(trimmed);
            return;
        }

        // 4. Rate-limit real server commands starting with '/' to prevent "You used a command too fast!"
        if (trimmed.startsWith("/") && !ctx.minecraft().isSingleplayer()) {
            long now = System.currentTimeMillis();
            boolean tooSoonAfterJoin = (now - worldJoinTime < 3500);
            boolean tooFastAfterLastCmd = (now - lastServerCommandTime < 1500);

            if (tooSoonAfterJoin || tooFastAfterLastCmd) {
                event.cancel();
                queuedServerCommands.offer(trimmed);
                long waitMs = tooSoonAfterJoin ? (3500 - (now - worldJoinTime)) : (1500 - (now - lastServerCommandTime));
                logDirect(String.format("§e[AntiKick] §7Đã hoãn lệnh §f%s §7(%d ms) để chống kick 'You used a command too fast'!", trimmed, waitMs));
                return;
            }
            lastServerCommandTime = now;
        }
    }

    private void logRanCommand(String command, String rest) {
        if (settings.echoCommands.value) {
            String msg = command + rest;
            String toDisplay = settings.censorRanCommands.value ? command + " ..." : msg;
            ITextComponent component = new TextComponentString(String.format("> %s", toDisplay));
            component.getStyle()
                    .setColor(TextFormatting.WHITE)
                    .setHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new TextComponentString("Click to rerun command")
                    ))
                    .setClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            FORCE_COMMAND_PREFIX + msg
                    ));
            logDirect(component);
        }
    }

    public boolean runCommand(String msg) {
        if (msg.trim().equalsIgnoreCase("damn")) {
            logDirect("daniel");
            return false;
        } else if (msg.trim().equalsIgnoreCase("orderpizza")) {
            try {
                ((IGuiScreen) ctx.minecraft().currentScreen).openLink(new URI("https://www.dominos.com/en/pages/order/"));
            } catch (NullPointerException | URISyntaxException ignored) {}
            return false;
        }
        if (msg.isEmpty()) {
            return this.runCommand("help");
        }
        Tuple<String, List<ICommandArgument>> pair = CommandManager.expand(msg);
        String command = pair.getFirst();
        String rest = msg.substring(pair.getFirst().length());
        ArgConsumer argc = new ArgConsumer(this.manager, pair.getSecond());
        if (!argc.hasAny()) {
            Settings.Setting setting = settings.byLowerName.get(command.toLowerCase(Locale.US));
            if (setting != null) {
                logRanCommand(command, rest);
                if (setting.getValueClass() == Boolean.class) {
                    this.manager.execute(String.format("set toggle %s", setting.getName()));
                } else {
                    this.manager.execute(String.format("set %s", setting.getName()));
                }
                return true;
            }
        } else if (argc.hasExactlyOne()) {
            for (Settings.Setting setting : settings.allSettings) {
                if (setting.isJavaOnly()) {
                    continue;
                }
                if (setting.getName().equalsIgnoreCase(pair.getFirst())) {
                    logRanCommand(command, rest);
                    try {
                        this.manager.execute(String.format("set %s %s", setting.getName(), argc.getString()));
                    } catch (CommandNotEnoughArgumentsException ignored) {} // The operation is safe
                    return true;
                }
            }
        }

        // If the command exists, then handle echoing the input
        if (this.manager.getCommand(pair.getFirst()) != null) {
            logRanCommand(command, rest);
        }

        return this.manager.execute(pair);
    }

    @Override
    public void onPreTabComplete(TabCompleteEvent event) {
        if (!settings.prefixControl.value) {
            return;
        }
        String prefix = event.prefix;
        String commandPrefix = settings.prefix.value;
        if (!prefix.startsWith(commandPrefix)) {
            return;
        }
        String msg = prefix.substring(commandPrefix.length());
        List<ICommandArgument> args = CommandArguments.from(msg, true);
        Stream<String> stream = tabComplete(msg);
        if (args.size() == 1) {
            stream = stream.map(x -> commandPrefix + x);
        }
        event.completions = stream.toArray(String[]::new);
    }

    public Stream<String> tabComplete(String msg) {
        try {
            List<ICommandArgument> args = CommandArguments.from(msg, true);
            ArgConsumer argc = new ArgConsumer(this.manager, args);
            if (argc.hasAtMost(2)) {
                if (argc.hasExactly(1)) {
                    return new TabCompleteHelper()
                            .addCommands(this.manager)
                            .addSettings()
                            .filterPrefix(argc.getString())
                            .stream();
                }
                Settings.Setting setting = settings.byLowerName.get(argc.getString().toLowerCase(Locale.US));
                if (setting != null && !setting.isJavaOnly()) {
                    if (setting.getValueClass() == Boolean.class) {
                        TabCompleteHelper helper = new TabCompleteHelper();
                        if ((Boolean) setting.value) {
                            helper.append("true", "false");
                        } else {
                            helper.append("false", "true");
                        }
                        return helper.filterPrefix(argc.getString()).stream();
                    } else {
                        return Stream.of(SettingsUtil.settingValueToString(setting));
                    }
                }
            }
            return this.manager.tabComplete(msg);
        } catch (CommandNotEnoughArgumentsException ignored) { // Shouldn't happen, the operation is safe
            return Stream.empty();
        }
    }
}
