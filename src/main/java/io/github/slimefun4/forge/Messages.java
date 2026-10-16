package io.github.slimefun4.forge;

import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Localized server messages; each caller owns its RC-37 prefix and channel choice. */
final class Messages {
    static final Pattern COLORS=Pattern.compile("(?i)[&§](?:x(?:[&§][0-9a-f]){6}|[0-9a-fk-or])");
    static Style color(Style style,String code){return code.length()>2?Style.EMPTY.withColor(Integer.parseInt(code.substring(2).replaceAll("[&§]",""),16)):style.applyLegacyFormat(ChatFormatting.getByCode(Character.toLowerCase(code.charAt(1))));}
    static Component format(ServerPlayer player,Component value,boolean addPrefix){
        var body=GuideContent.localize(player==null?Guide.LANGUAGE.get():Guide.locale(player),value);
        if(!addPrefix)return player==null?Component.literal(ChatFormatting.stripFormatting(body.getString())):body;
        var result=Component.empty();var style=Style.EMPTY;
        if(addPrefix){
            String prefix=Guide.CHAT_PREFIX.get();var codes=COLORS.matcher(prefix);int start=0;
            while(codes.find()){
                result.append(Component.literal(prefix.substring(start,codes.start())).setStyle(style));String code=codes.group();
                style=color(style,code);start=codes.end();
            }
            result.append(Component.literal(prefix.substring(start)).setStyle(style));
        }
        result.append(body.copy().setStyle(body.getStyle().applyTo(style)));
        return player==null?Component.literal(ChatFormatting.stripFormatting(result.getString())):result;
    }
    static Component format(CommandSourceStack source,Component value,boolean prefix){return format(source.getEntity() instanceof ServerPlayer player?player:null,value,prefix);}
    static void chat(Player player,Component value){send(player,value,false,true);}
    static void lines(ServerPlayer player,String key,Object...args){var language=GuideContent.language(Guide.locale(player));for(int i=0;language.containsKey(key+"."+i);i++)chat(player,Component.translatable(key+"."+i,args));}
    static void send(Player player,Component value,boolean actionbar,boolean prefix){if(player instanceof ServerPlayer server)server.displayClientMessage(format(server,value,prefix),actionbar);}
    static void command(CommandSourceStack source,Component value,boolean prefix){source.sendSuccess(()->format(source,value,prefix),false);}
    private Messages(){}
}
