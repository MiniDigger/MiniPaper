package net.minecraft.server;

import com.google.common.collect.Lists;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.Tag;
import net.minecraft.world.level.GameRules;

public class ServerFunctionManager {

    private static final ResourceLocation TICK_FUNCTION_TAG = new ResourceLocation("tick");
    private static final ResourceLocation LOAD_FUNCTION_TAG = new ResourceLocation("load");
    private final MinecraftServer server;
    private boolean isInFunction;
    private final ArrayDeque<ServerFunctionManager.QueuedCommand> commandQueue = new ArrayDeque();
    private final List<ServerFunctionManager.QueuedCommand> nestedCalls = Lists.newArrayList();
    private final List<CommandFunction> ticking = Lists.newArrayList();
    private boolean postReload;
    private ServerFunctionLibrary library;

    public ServerFunctionManager(MinecraftServer minecraftserver, ServerFunctionLibrary customfunctionmanager) {
        this.server = minecraftserver;
        this.library = customfunctionmanager;
        this.postReload(customfunctionmanager);
    }

    public int getCommandLimit() {
        return this.server.getGameRules().getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH);
    }

    public com.mojang.brigadier.CommandDispatcher<CommandSourceStack> getDispatcher() {
        return this.server.vanillaCommandDispatcher.getDispatcher(); // CraftBukkit
    }

    public void tick() {
        this.executeTagFunctions((Collection) this.ticking, ServerFunctionManager.TICK_FUNCTION_TAG);
        if (this.postReload) {
            this.postReload = false;
            Collection<CommandFunction> collection = this.library.getTags().getTagOrEmpty(ServerFunctionManager.LOAD_FUNCTION_TAG).getValues();

            this.executeTagFunctions((Collection) collection, ServerFunctionManager.LOAD_FUNCTION_TAG);
        }

    }

    private void executeTagFunctions(Collection<CommandFunction> collection, ResourceLocation minecraftkey) {
        this.server.getProfiler().push(minecraftkey::toString);
        Iterator iterator = collection.iterator();

        while (iterator.hasNext()) {
            CommandFunction customfunction = (CommandFunction) iterator.next();

            this.execute(customfunction, this.getGameLoopSender());
        }

        this.server.getProfiler().pop();
    }

    public int execute(CommandFunction customfunction, CommandSourceStack commandlistenerwrapper) {
        int i = this.getCommandLimit();

        if (this.isInFunction) {
            if (this.commandQueue.size() + this.nestedCalls.size() < i) {
                this.nestedCalls.add(new ServerFunctionManager.QueuedCommand(this, commandlistenerwrapper, new CommandFunction.FunctionEntry(customfunction)));
            }

            return 0;
        } else {
            int j;

            try {
                this.isInFunction = true;
                int k = 0;
                CommandFunction.Entry[] acustomfunction_c = customfunction.getEntries();

                for (j = acustomfunction_c.length - 1; j >= 0; --j) {
                    this.commandQueue.push(new ServerFunctionManager.QueuedCommand(this, commandlistenerwrapper, acustomfunction_c[j]));
                }

                do {
                    if (this.commandQueue.isEmpty()) {
                        j = k;
                        return j;
                    }

                    try {
                        ServerFunctionManager.QueuedCommand customfunctiondata_a = (ServerFunctionManager.QueuedCommand) this.commandQueue.removeFirst();

                        this.server.getProfiler().push(customfunctiondata_a::toString);
                        customfunctiondata_a.execute(this.commandQueue, i);
                        if (!this.nestedCalls.isEmpty()) {
                            List list = Lists.reverse(this.nestedCalls);
                            ArrayDeque arraydeque = this.commandQueue;

                            this.commandQueue.getClass();
                            list.forEach(arraydeque::addFirst);
                            this.nestedCalls.clear();
                        }
                    } finally {
                        this.server.getProfiler().pop();
                    }

                    ++k;
                } while (k < i);

                j = k;
            } finally {
                this.commandQueue.clear();
                this.nestedCalls.clear();
                this.isInFunction = false;
            }

            return j;
        }
    }

    public void replaceLibrary(ServerFunctionLibrary customfunctionmanager) {
        this.library = customfunctionmanager;
        this.postReload(customfunctionmanager);
    }

    private void postReload(ServerFunctionLibrary customfunctionmanager) {
        this.ticking.clear();
        this.ticking.addAll(customfunctionmanager.getTags().getTagOrEmpty(ServerFunctionManager.TICK_FUNCTION_TAG).getValues());
        this.postReload = true;
    }

    public CommandSourceStack getGameLoopSender() {
        return this.server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
    }

    public Optional<CommandFunction> get(ResourceLocation minecraftkey) {
        return this.library.getFunction(minecraftkey);
    }

    public Tag<CommandFunction> getTag(ResourceLocation minecraftkey) {
        return this.library.getTag(minecraftkey);
    }

    public Iterable<ResourceLocation> getFunctionNames() {
        return this.library.getFunctions().keySet();
    }

    public Iterable<ResourceLocation> getTagNames() {
        return this.library.getTags().getAvailableTags();
    }

    public static class QueuedCommand {

        private final ServerFunctionManager manager;
        private final CommandSourceStack sender;
        private final CommandFunction.Entry entry;

        public QueuedCommand(ServerFunctionManager customfunctiondata, CommandSourceStack commandlistenerwrapper, CommandFunction.Entry customfunction_c) {
            this.manager = customfunctiondata;
            this.sender = commandlistenerwrapper;
            this.entry = customfunction_c;
        }

        public void execute(ArrayDeque<ServerFunctionManager.QueuedCommand> arraydeque, int i) {
            try {
                this.entry.execute(this.manager, this.sender, arraydeque, i);
            } catch (Throwable throwable) {
                ;
            }

        }

        public String toString() {
            return this.entry.toString();
        }
    }
}
