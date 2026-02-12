package com.dnsmanager.services;

import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

public class BackupService {
    private final Path backupDir;
    private final int maxBackups;
    
    public BackupService(Path backupDir, int maxBackups) {
        this.backupDir = backupDir;
        this.maxBackups = maxBackups;
    }
    
    public Path createBackup(Path zoneFile) throws IOException {
        Files.createDirectories(backupDir);
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String backupFilename = zoneFile.getFileName() + "." + timestamp;
        Path backupFile = backupDir.resolve(backupFilename);
        
        Files.copy(zoneFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        
        cleanupOldBackups(zoneFile.getFileName().toString());
        
        return backupFile;
    }
    
    public List<Path> listBackups(String zoneFileName) throws IOException {
        if (!Files.exists(backupDir)) {
            return Collections.emptyList();
        }
        
        List<Path> backups = new ArrayList<>();
        try (Stream<Path> paths = Files.list(backupDir)) {
            paths.filter(p -> p.getFileName().toString().startsWith(zoneFileName))
                 .sorted(Comparator.comparing(Path::toString).reversed())
                 .forEach(backups::add);
        }
        
        return backups;
    }
    
    public void restoreBackup(Path backupFile, Path targetFile) throws IOException {
        Files.copy(backupFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }
    
    private void cleanupOldBackups(String zoneFileName) throws IOException {
        List<Path> backups = listBackups(zoneFileName);
        
        if (backups.size() > maxBackups) {
            for (int i = maxBackups; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
            }
        }
    }
    
    public Path getBackupDir() {
        return backupDir;
    }
}
