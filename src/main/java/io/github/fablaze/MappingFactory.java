package io.github.fablaze;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.jar.JarFile;

import com.opencsv.CSVReader;

import org.cadixdev.bombe.asm.jar.ClassProvider;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.asm.AsmFieldTypeProvider;
import org.cadixdev.lorenz.io.srg.SrgReader;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;

import net.fabricmc.lorenztiny.TinyMappingsWriter;

public class MappingFactory {
    static Map<String, String> methods = new HashMap<>();
    static Map<String, String> fields = new HashMap<>();
    public static void main(String[] args) {
        try {
            MappingSet clientSrg = null;
            try (SrgReader reader = new SrgReader(Files.newBufferedReader(Path.of(".", "mcp", "client.srg")))) {
                clientSrg = reader.read();
            }
            MappingSet serverSrg = null;
            try (SrgReader reader = new SrgReader(Files.newBufferedReader(Path.of(".", "mcp", "server.srg")))) {
                serverSrg = reader.read();
            }

            MappingSet merged = clientSrg.merge(serverSrg);
            
            try (CSVReader reader = new CSVReader(Files.newBufferedReader(Path.of(".", "mcp", "methods.csv")))) {
                String[] record;
                while ((record = reader.readNext()) != null) {
                    methods.put(record[0], record[1]);
                }
            }
            
            try (CSVReader reader = new CSVReader(Files.newBufferedReader(Path.of(".", "mcp", "fields.csv")))) {
                String[] record;
                while ((record = reader.readNext()) != null) {
                    fields.put(record[0], record[1]);
                }
            }

            MappingSet fablaze = MappingSet.create();
            fablaze.addFieldTypeProvider(new AsmFieldTypeProvider(ClassProvider.of(new JarFile("client.jar"))));
            fablaze.addFieldTypeProvider(new AsmFieldTypeProvider(ClassProvider.of(new JarFile("server.jar"))));
            for (TopLevelClassMapping topLevelClassMapping : merged.getTopLevelClassMappings()) {
                TopLevelClassMapping fTopLevelClassMapping = fablaze.createTopLevelClassMapping(topLevelClassMapping.getObfuscatedName(), remapClass(topLevelClassMapping.getDeobfuscatedName()));
                for (FieldMapping fieldMapping : topLevelClassMapping.getFieldMappings()) {
                    fTopLevelClassMapping.createFieldMapping(fieldMapping.getSignature(), remapField(fieldMapping.getDeobfuscatedName()));
                }
                for (MethodMapping methodMapping : topLevelClassMapping.getMethodMappings()) {
                    fTopLevelClassMapping.createMethodMapping(methodMapping.getSignature(), remapMethod(methodMapping.getDeobfuscatedName()));
                }
            }

            try (TinyMappingsWriter writer = new TinyMappingsWriter(Files.newBufferedWriter(Path.of(".", "out.tiny")), "164", "fablaze")) {
                writer.write(fablaze);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final String NMS = "net/minecraft/src/";
    private static final int NMS_LEN = NMS.length();

    private static final String NMS2 = "net/minecraft/server/";
    private static final int NMS2_LEN = NMS2.length();

    private static final String NMC = "net/minecraft/client/";
    private static final int NMC_LEN = NMC.length();

    public static String remapMethod(String deobfName) {
        return methods.computeIfAbsent(deobfName, d -> {
            System.out.println("Missing method mapping for: " + d);
            return d;
        });
    }

    public static String remapField(String deobfName) {
        return fields.computeIfAbsent(deobfName, d -> {
            System.out.println("Missing field mapping for: " + d);
            return d;
        });
    }

    public static String remapClass(String deobfName) {
        int a = deobfName.indexOf(NMS);
        if (a >= 0) {
            return "io/github/fablaze/mcwrapper/F_" + deobfName.substring(a + NMS_LEN);
        }
        int b = deobfName.indexOf(NMS2);
        if (b >= 0) {
            return "io/github/fablaze/mcwrapper/server/F_" + deobfName.substring(b + NMS2_LEN);
        }
        int c = deobfName.indexOf(NMC);
        if (c >= 0) {
            return "io/github/fablaze/mcwrapper/client/F_" + deobfName.substring(c + NMC_LEN);
        }
        throw new UnsupportedOperationException(deobfName);
    }
}
