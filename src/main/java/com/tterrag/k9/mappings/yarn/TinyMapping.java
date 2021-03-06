package com.tterrag.k9.mappings.yarn;

import java.util.EnumMap;
import java.util.Map;

import com.tterrag.k9.mappings.Mapping;
import com.tterrag.k9.mappings.MappingDatabase;
import com.tterrag.k9.mappings.MappingType;
import com.tterrag.k9.mappings.NameType;
import com.tterrag.k9.mappings.SignatureHelper;
import com.tterrag.k9.util.annotation.NonNull;
import com.tterrag.k9.util.annotation.Nullable;

import clojure.asm.Type;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;

@Value
@RequiredArgsConstructor
@EqualsAndHashCode(of = {"type", "intermediate"})
@ToString(doNotUseGetters = true)
public class TinyMapping implements Mapping {
    
    private static final SignatureHelper sigHelper = new SignatureHelper();
    
    @ToString.Exclude
    transient MappingDatabase<@NonNull TinyMapping> db;
    
    @Getter(onMethod = @__(@Override))
    MappingType type;
    
    String owner, desc;
    
    @Getter(onMethod = @__(@Override))
    String original, intermediate, name;
    
    @ToString.Exclude
    transient Map<NameType, String> mappedOwner = new EnumMap<>(NameType.class), mappedDesc = new EnumMap<>(NameType.class);
    
    @Override
    public @Nullable String getOwner() {
        return getOwner(NameType.NAME);
    }
    
    @Override
    public @Nullable String getOwner(NameType name) {
        return mappedOwner.computeIfAbsent(name, t -> owner == null ? null : sigHelper.mapType(t, owner, this, db).getInternalName());
    }
    
    @Override
    public @Nullable String getDesc() {
        return getType() == MappingType.METHOD ? getDesc(NameType.NAME) : null;
    }
    
    @Override
    public @Nullable String getMemberClass() {
        return getType() == MappingType.FIELD ? Type.getType(getDesc(NameType.NAME)).getClassName() : Mapping.super.getMemberClass();
    }
    
    private String mapType(NameType t, String type) {
        return sigHelper.mapType(t, Type.getType(type), this, db).getDescriptor();
    }
    
    @Override
    public @Nullable String getDesc(NameType name) {
        return mappedDesc.computeIfAbsent(name, t -> desc == null ? null : desc.contains("(") ? sigHelper.mapSignature(t, desc, this, db) : mapType(t, desc));
    }
    
    @Override
    public String formatMessage(String mcver) {
        StringBuilder builder = new StringBuilder();
        String name = getName();
        String displayName = name;
        if (displayName == null) {
            displayName = getIntermediate().replaceAll("_", "\\_");
        }
        builder.append("\n");
        String owner = getOwner();
        builder.append("**MC " + mcver + ": " + (owner != null ? owner + "." : "") + displayName + "**\n");
        builder.append("__Name__: " + (getType() == MappingType.PARAM ? "`" : getOriginal() + " => `") + getIntermediate() + (name == null ? "`\n" : "` => `" + getName() + "`\n"));
        String desc = getDesc();
        if (desc != null) {
            builder.append("__Descriptor__: `" + displayName + desc + "`\n");
        }
        String type = getMemberClass();
        if (type != null) {
            builder.append("__Type__: `" + type  + "`\n");
        }
        String mixinTarget = null;
        if (owner != null) {
            if (getType() == MappingType.METHOD && desc != null) {
                mixinTarget = owner + "." + displayName + desc;
            } else if (getType() == MappingType.FIELD && type != null) {
                mixinTarget = "L" + owner + ";" + displayName + ":" + getDesc(NameType.NAME);
            }
        }
        if (mixinTarget != null) {
            builder.append("__Mixin Target__: `").append(mixinTarget).append("`\n");
        }
        return builder.toString();
    }
    
    public static TinyMapping fromString(MappingDatabase<@NonNull TinyMapping> db, String line, IntList order) {
        String[] info = line.split("\t");
        MappingType type = MappingType.valueOf(info[0]);
        switch(type) {
            case CLASS:
                String intermediate = info[order.getInt(1)];
                String name = info[order.getInt(2)];
                return new TinyMapping(db, type, null, null, info[order.getInt(0)], intermediate, intermediate.equals(name) ? null : name);
            case METHOD:
            case FIELD:
                intermediate = info[order.getInt(1) + 2];
                name = info[order.getInt(2) + 2];
                return new TinyMapping(db, type, info[1], info[2], info[order.getInt(0) + 2], intermediate, intermediate.equals(name) ? null : name);
            default:
                throw new IllegalArgumentException("Unknown type"); // Params NYI, doesn't exist in the spec
        }
    }

}
