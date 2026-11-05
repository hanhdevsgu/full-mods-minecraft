package com.autorefill;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.common.MinecraftForge;

public class BaritoneIntegration {
   private static Boolean baritonePresent = null;
   private static Method getProviderMethod = null;
   private static Method getPrimaryBaritoneMethod = null;
   private static Method getCommandManagerMethod = null;
   private static Method executeMethod = null;
   private static Method getBuilderProcessMethod = null;
   private static Method isPausedMethod = null;
   private static Method isActiveMethod = null;
   private static Method builderResumeMethod = null;
   private static Method builderPauseMethod = null;
   private static String lastCommand = null;

   public static void setLastCommand(String cmd) {
      if (cmd != null && !cmd.trim().isEmpty()) {
         String clean = cmd.trim();
         if (clean.startsWith("#") || clean.startsWith(".") || clean.startsWith("@")) {
            clean = clean.substring(1).trim();
         }

         lastCommand = clean;
      } else {
         lastCommand = null;
      }
   }

   public static void clearLastCommand() {
      lastCommand = null;
   }

   public static String getLastCommand() {
      return lastCommand;
   }

   private static void init() {
      if (baritonePresent == null) {
         try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            getProviderMethod = apiClass.getMethod("getProvider");
            Object provider = getProviderMethod.invoke(null);
            if (provider == null) {
               baritonePresent = false;
               return;
            }

            getPrimaryBaritoneMethod = provider.getClass().getMethod("getPrimaryBaritone");
            Object primary = getPrimaryBaritoneMethod.invoke(provider);
            if (primary == null) {
               baritonePresent = false;
               return;
            }

            getCommandManagerMethod = primary.getClass().getMethod("getCommandManager");
            Object cmdManager = getCommandManagerMethod.invoke(primary);
            executeMethod = cmdManager.getClass().getMethod("execute", String.class);

            try {
               getBuilderProcessMethod = primary.getClass().getMethod("getBuilderProcess");
               Object builder = getBuilderProcessMethod.invoke(primary);
               if (builder != null) {
                  try {
                     isPausedMethod = builder.getClass().getMethod("isPaused");
                  } catch (Throwable var9) {
                  }

                  try {
                     isActiveMethod = builder.getClass().getMethod("isActive");
                  } catch (Throwable var8) {
                  }

                  try {
                     builderResumeMethod = builder.getClass().getMethod("resume");
                  } catch (Throwable var7) {
                  }

                  try {
                     builderPauseMethod = builder.getClass().getMethod("pause");
                  } catch (Throwable var6) {
                  }
               }
            } catch (Throwable var10) {
            }

            baritonePresent = true;
            System.out.println("[AutoRefill] Baritone API tích hợp thành công qua Reflection!");
         } catch (Throwable var11) {
            baritonePresent = false;
            System.out.println("[AutoRefill] Không tìm thấy Baritone API trong classpath, sẽ sử dụng fallback qua lệnh chat (#resume).");
         }
      }
   }

   public static void disableBaritoneSprint() {
      try {
         Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
         Method getSettingsMethod = apiClass.getMethod("getSettings");
         Object settings = getSettingsMethod.invoke(null);
         if (settings != null) {
            try {
               Field allowSprintField = settings.getClass().getField("allowSprint");
               Object allowSprintSetting = allowSprintField.get(settings);
               if (allowSprintSetting != null) {
                  Field valField = allowSprintSetting.getClass().getField("value");
                  valField.set(allowSprintSetting, false);
               }
            } catch (Throwable var7) {
            }

            try {
               Field sprintAscendsField = settings.getClass().getField("sprintAscends");
               Object sprintAscendsSetting = sprintAscendsField.get(settings);
               if (sprintAscendsSetting != null) {
                  Field valField = sprintAscendsSetting.getClass().getField("value");
                  valField.set(sprintAscendsSetting, false);
               }
            } catch (Throwable var6) {
            }

            System.out.println("[AutoRefill] Đã tắt allowSprint trong Baritone -> Giữ nguyên thanh feed!");
         }
      } catch (Throwable var8) {
         execute("set allowSprint false");
         execute("set sprintAscends false");
      }
   }

   public static boolean isBaritoneAvailable() {
      init();
      return Boolean.TRUE.equals(baritonePresent);
   }

   public static boolean isBuilderPaused() {
      init();
      if (isBaritoneAvailable() && getBuilderProcessMethod != null && isPausedMethod != null) {
         try {
            Object provider = getProviderMethod.invoke(null);
            Object primary = getPrimaryBaritoneMethod.invoke(provider);
            Object builder = getBuilderProcessMethod.invoke(primary);
            if (builder != null) {
               return (Boolean)isPausedMethod.invoke(builder);
            }
         } catch (Throwable var3) {
         }
      }

      return false;
   }

   public static boolean isBuilderActive() {
      init();
      if (isBaritoneAvailable() && getBuilderProcessMethod != null && isActiveMethod != null) {
         try {
            Object provider = getProviderMethod.invoke(null);
            Object primary = getPrimaryBaritoneMethod.invoke(provider);
            Object builder = getBuilderProcessMethod.invoke(primary);
            if (builder != null) {
               return (Boolean)isActiveMethod.invoke(builder);
            }
         } catch (Throwable var3) {
         }
      }

      return false;
   }

   public static void pause() {
      init();
      if (isBaritoneAvailable() && executeMethod != null) {
         try {
            Object provider = getProviderMethod.invoke(null);
            Object primary = getPrimaryBaritoneMethod.invoke(provider);
            Object cmdManager = getCommandManagerMethod.invoke(primary);
            executeMethod.invoke(cmdManager, "pause");
            if (getBuilderProcessMethod != null && builderPauseMethod != null) {
               Object builder = getBuilderProcessMethod.invoke(primary);
               if (builder != null) {
                  builderPauseMethod.invoke(builder);
               }
            }

            return;
         } catch (Throwable var4) {
         }
      }

      sendClientOnlyMessage("#pause");
   }

   public static void resume() {
      init();
      boolean resumedViaApi = false;
      if (isBaritoneAvailable() && executeMethod != null) {
         try {
            Object provider = getProviderMethod.invoke(null);
            Object primary = getPrimaryBaritoneMethod.invoke(provider);
            Object cmdManager = getCommandManagerMethod.invoke(primary);
            executeMethod.invoke(cmdManager, "resume");
            if (getBuilderProcessMethod != null && builderResumeMethod != null) {
               Object builder = getBuilderProcessMethod.invoke(primary);
               if (builder != null) {
                  builderResumeMethod.invoke(builder);
               }
            }

            resumedViaApi = true;
            if (getBuilderProcessMethod != null && isActiveMethod != null) {
               Object builder = getBuilderProcessMethod.invoke(primary);
               if (builder != null) {
                  boolean active = (Boolean)isActiveMethod.invoke(builder);
                  if (!active && lastCommand != null && !lastCommand.isEmpty()) {
                     executeMethod.invoke(cmdManager, lastCommand);
                  }
               }
            }
         } catch (Throwable var6) {
         }
      }

      if (!resumedViaApi) {
         sendClientOnlyMessage("#resume");
      }
   }

   public static boolean setBaritoneGoalDirect(int x, int y, int z) {
      try {
         Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
         Object provider = apiClass.getMethod("getProvider").invoke(null);
         if (provider == null) {
            return false;
         }

         Object primary = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
         if (primary == null) {
            return false;
         }

         Class<?> goalBlockClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
         Object goal = goalBlockClass.getConstructor(int.class, int.class, int.class).newInstance(x, y, z);
         Object customGoal = primary.getClass().getMethod("getCustomGoalProcess").invoke(primary);
         if (customGoal != null) {
            Class<?> goalClass = Class.forName("baritone.api.pathing.goals.Goal");
            Method setGoalAndPath = customGoal.getClass().getMethod("setGoalAndPath", goalClass);
            setGoalAndPath.invoke(customGoal, goal);
            System.out.println("[BaritoneIntegration] Da dat GoalBlock truc tiep toi (" + x + ", " + y + ", " + z + ") qua Baritone API!");
            return true;
         }
      } catch (Throwable var11) {
      }

      return false;
   }

   public static boolean setBaritoneGoalXZDirect(int x, int z) {
      try {
         Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
         Object provider = apiClass.getMethod("getProvider").invoke(null);
         if (provider == null) {
            return false;
         }

         Object primary = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
         if (primary == null) {
            return false;
         }

         Class<?> goalXZClass = Class.forName("baritone.api.pathing.goals.GoalXZ");
         Object goal = goalXZClass.getConstructor(int.class, int.class).newInstance(x, z);
         Object customGoal = primary.getClass().getMethod("getCustomGoalProcess").invoke(primary);
         if (customGoal != null) {
            Class<?> goalClass = Class.forName("baritone.api.pathing.goals.Goal");
            Method setGoalAndPath = customGoal.getClass().getMethod("setGoalAndPath", goalClass);
            setGoalAndPath.invoke(customGoal, goal);
            System.out.println("[BaritoneIntegration] Da dat GoalXZ truc tiep toi (" + x + ", " + z + ") qua Baritone API!");
            return true;
         }
      } catch (Throwable var10) {
      }

      return false;
   }

   public static boolean cancelBaritoneDirect() {
      try {
         Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
         Object provider = apiClass.getMethod("getProvider").invoke(null);
         if (provider == null) {
            return false;
         }

         Object primary = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
         if (primary == null) {
            return false;
         }

         Object pathingBehavior = primary.getClass().getMethod("getPathingBehavior").invoke(primary);
         if (pathingBehavior != null) {
            pathingBehavior.getClass().getMethod("cancel").invoke(pathingBehavior);
            System.out.println("[BaritoneIntegration] Da huy pathfinding truc tiep qua Baritone PathingBehavior!");
            return true;
         }
      } catch (Throwable var4) {
      }

      return false;
   }

   public static boolean executeMinClientOrImpact(String rawCommand) {
      if (rawCommand != null && !rawCommand.trim().isEmpty()) {
         String clean = rawCommand.trim();
         boolean handled = false;

         try {
            Class<?> minClientClass = Class.forName("com.minclient.MinClientMod");
            Field instField = minClientClass.getField("INSTANCE");
            Object minClient = instField.get(null);
            if (minClient != null) {
               Method getCmdMgr = minClient.getClass().getMethod("getCommandManager");
               Object cmdMgr = getCmdMgr.invoke(minClient);
               if (cmdMgr != null) {
                  try {
                     Method handleChat = cmdMgr.getClass().getMethod("handleChat", String.class);
                     handleChat.invoke(cmdMgr, clean);
                     handled = true;
                  } catch (NoSuchMethodException var12) {
                     Method execute = cmdMgr.getClass().getMethod("execute", String.class);
                     execute.invoke(cmdMgr, clean);
                     handled = true;
                  }
               }
            }
         } catch (Throwable var13) {
         }

         if (!handled) {
            try {
               Class<?> impactClass = Class.forName("impact.Impact");
               Method getInstance = impactClass.getMethod("getInstance");
               Object impact = getInstance.invoke(null);
               if (impact != null) {
                  Method getCmdMgr = impact.getClass().getMethod("getCommandManager");
                  Object cmdMgr = getCmdMgr.invoke(impact);
                  if (cmdMgr != null) {
                     try {
                        Method execute = cmdMgr.getClass().getMethod("execute", String.class);
                        execute.invoke(cmdMgr, clean);
                        handled = true;
                     } catch (Throwable var10) {
                        Method handleChat = cmdMgr.getClass().getMethod("handleChat", String.class);
                        handleChat.invoke(cmdMgr, clean);
                        handled = true;
                     }
                  }
               }
            } catch (Throwable var11) {
            }
         }

         return handled;
      } else {
         return false;
      }
   }

   public static void execute(String command) {
      if (command != null && !command.trim().isEmpty()) {
         init();
         String raw = command.trim();
         String clean = raw;
         if (raw.startsWith("#") || raw.startsWith(".") || raw.startsWith("@")) {
            clean = raw.substring(1).trim();
         }

         if (clean.toLowerCase().startsWith("goto ")) {
            String[] parts = clean.split("\\s+");
            if (parts.length >= 4) {
               try {
                  int x = Integer.parseInt(parts[1]);
                  int y = Integer.parseInt(parts[2]);
                  int z = Integer.parseInt(parts[3]);
                  setBaritoneGoalDirect(x, y, z);
               } catch (Throwable var8) {
               }
            } else if (parts.length == 3) {
               try {
                  int x = Integer.parseInt(parts[1]);
                  int z = Integer.parseInt(parts[2]);
                  setBaritoneGoalXZDirect(x, z);
               } catch (Throwable var7) {
               }
            }
         } else if (clean.equalsIgnoreCase("stop") || clean.equalsIgnoreCase("cancel")) {
            cancelBaritoneDirect();
         }

         if (isBaritoneAvailable() && executeMethod != null) {
            try {
               Object provider = getProviderMethod.invoke(null);
               Object primary = getPrimaryBaritoneMethod.invoke(provider);
               Object cmdManager = getCommandManagerMethod.invoke(primary);
               executeMethod.invoke(cmdManager, clean);
               return;
            } catch (Throwable var9) {
            }
         }

         if (!executeMinClientOrImpact(raw)) {
            sendClientOnlyMessage(!raw.startsWith("#") && !raw.startsWith(".") ? "#" + clean : raw);
         }
      }
   }

   private static void sendClientOnlyMessage(String msg) {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.player != null) {
         ClientChatEvent event = new ClientChatEvent(msg);
         MinecraftForge.EVENT_BUS.post(event);
      }
   }
}
