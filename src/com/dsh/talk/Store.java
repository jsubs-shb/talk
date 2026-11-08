package com.dsh.talk;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** 本地存储：文件夹 + 会话 + 消息 */
public class Store extends SQLiteOpenHelper {

    private static final String DB = "talk.db";
    private static final int V = 5;

    public static final long FOLDER_NONE = 0;  // 未分类

    public static class Folder {
        public long id;
        public String name;
        public int sortOrder;
    }

    public static class Conv {
        public long id;
        public String title;
        public long updated;
        public int autoNamed;
        public int pinned;
        public long folderId;   // 0 = 未分类
        /** 最后一条消息摘要 */
        public String preview;
        /** 消息条数 */
        public int count;
    }

    public static class Msg {
        public long id;
        public long conv;
        public String role;
        public String text;
        public String trans;
        public long at;
        public String audio;
    }

    public Store(Context c) {
        super(c, DB, null, V);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE folder(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " name TEXT, sort_order INTEGER DEFAULT 0, uuid TEXT)");
        db.execSQL("CREATE TABLE conv(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " title TEXT, updated INTEGER, auto_named INTEGER DEFAULT 0,"
                + " pinned INTEGER DEFAULT 0, folder_id INTEGER DEFAULT 0, uuid TEXT)");
        db.execSQL("CREATE TABLE msg(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " conv INTEGER, role TEXT, text TEXT, trans TEXT, at INTEGER, audio TEXT,"
                + " uuid TEXT)");
        db.execSQL("CREATE INDEX idx_msg_conv ON msg(conv)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 5) {
            try { db.execSQL("ALTER TABLE conv ADD COLUMN uuid TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE msg ADD COLUMN uuid TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE folder ADD COLUMN uuid TEXT"); } catch (Exception ignored) {}
            backfillUuids(db);
        }
        if (o < 2) {
            try { db.execSQL("ALTER TABLE msg ADD COLUMN trans TEXT"); } catch (Exception ignored) {}
        }
        if (o < 3) {
            try { db.execSQL("ALTER TABLE conv ADD COLUMN pinned INTEGER DEFAULT 0"); } catch (Exception ignored) {}
        }
        if (o < 4) {
            try {
                db.execSQL("CREATE TABLE folder(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + " name TEXT, sort_order INTEGER DEFAULT 0)");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE conv ADD COLUMN folder_id INTEGER DEFAULT 0");
            } catch (Exception ignored) {}
        }
    }

    // ── 文件夹 ──────────────────────────────────────────────────────

    public List<Folder> listFolders() {
        List<Folder> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT _id,name,sort_order FROM folder ORDER BY sort_order ASC,_id ASC", null)) {
            while (c.moveToNext()) {
                Folder f = new Folder();
                f.id = c.getLong(0);
                f.name = c.getString(1);
                f.sortOrder = c.getInt(2);
                out.add(f);
            }
        }
        return out;
    }

    /** 给已有记录补上永久 id */
    private void backfillUuids(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("SELECT _id FROM conv WHERE uuid IS NULL", null)) {
            java.util.List<Long> ids = new java.util.ArrayList<>();
            while (c.moveToNext()) ids.add(c.getLong(0));
            for (Long id : ids) {
                ContentValues cv = new ContentValues();
                cv.put("uuid", java.util.UUID.randomUUID().toString());
                db.update("conv", cv, "_id=?", new String[]{String.valueOf(id)});
            }
        } catch (Exception ignored) {}
        try (Cursor c = db.rawQuery("SELECT _id FROM msg WHERE uuid IS NULL", null)) {
            java.util.List<Long> ids = new java.util.ArrayList<>();
            while (c.moveToNext()) ids.add(c.getLong(0));
            for (Long id : ids) {
                ContentValues cv = new ContentValues();
                cv.put("uuid", java.util.UUID.randomUUID().toString());
                db.update("msg", cv, "_id=?", new String[]{String.valueOf(id)});
            }
        } catch (Exception ignored) {}
        try (Cursor c = db.rawQuery("SELECT _id FROM folder WHERE uuid IS NULL", null)) {
            java.util.List<Long> ids = new java.util.ArrayList<>();
            while (c.moveToNext()) ids.add(c.getLong(0));
            for (Long id : ids) {
                ContentValues cv = new ContentValues();
                cv.put("uuid", java.util.UUID.randomUUID().toString());
                db.update("folder", cv, "_id=?", new String[]{String.valueOf(id)});
            }
        } catch (Exception ignored) {}
    }

    public long createFolder(String name) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("sort_order", 0);
        cv.put("uuid", java.util.UUID.randomUUID().toString());
        return getWritableDatabase().insert("folder", null, cv);
    }

    public void renameFolder(long id, String name) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        getWritableDatabase().update("folder", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteFolder(long id) {
        // 文件夹下的对话移回未分类
        ContentValues cv = new ContentValues();
        cv.put("folder_id", 0);
        getWritableDatabase().update("conv", cv, "folder_id=?", new String[]{String.valueOf(id)});
        getWritableDatabase().delete("folder", "_id=?", new String[]{String.valueOf(id)});
    }

    public void setFolderOrder(long id, int order) {
        ContentValues cv = new ContentValues();
        cv.put("sort_order", order);
        getWritableDatabase().update("folder", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    /** 把对话移到文件夹（folderId=0 表示移出到未分类） */
    public void moveConvToFolder(long convId, long folderId) {
        ContentValues cv = new ContentValues();
        cv.put("folder_id", folderId);
        getWritableDatabase().update("conv", cv, "_id=?", new String[]{String.valueOf(convId)});
    }

    // ── 会话 ────────────────────────────────────────────────────────

    public List<Conv> listConvs() {
        List<Conv> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT _id,title,updated,auto_named,pinned,folder_id FROM conv"
                        + " ORDER BY pinned DESC, updated DESC", null)) {
            while (c.moveToNext()) {
                Conv v = new Conv();
                v.id = c.getLong(0);
                v.title = c.getString(1);
                v.updated = c.getLong(2);
                v.autoNamed = c.getInt(3);
                v.pinned = c.getInt(4);
                v.folderId = c.getLong(5);
                try (Cursor m = getReadableDatabase().rawQuery(
                        "SELECT text,(SELECT COUNT(*) FROM msg WHERE conv=?) FROM msg WHERE conv=? ORDER BY _id DESC LIMIT 1",
                        new String[]{String.valueOf(v.id), String.valueOf(v.id)})) {
                    if (m.moveToNext()) {
                        String txt = m.getString(0);
                        v.preview = txt == null ? null : (txt.length() > 50 ? txt.substring(0, 50) + "…" : txt);
                        v.count = m.getInt(1);
                    }
                }
                out.add(v);
            }
        }
        return out;
    }

    public long createConv(String title) {
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("updated", System.currentTimeMillis());
        cv.put("auto_named", 0);
        cv.put("pinned", 0);
        cv.put("folder_id", 0);
        cv.put("uuid", java.util.UUID.randomUUID().toString());
        return getWritableDatabase().insert("conv", null, cv);
    }

    public void touchConv(long id, String titleIfNull) {
        ContentValues cv = new ContentValues();
        cv.put("updated", System.currentTimeMillis());
        if (titleIfNull != null) cv.put("title", titleIfNull);
        getWritableDatabase().update("conv", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    public void renameConv(long id, String title, boolean markManual) {
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        if (markManual) cv.put("auto_named", 1);
        getWritableDatabase().update("conv", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    public void setPinned(long id, boolean pinned) {
        ContentValues cv = new ContentValues();
        cv.put("pinned", pinned ? 1 : 0);
        getWritableDatabase().update("conv", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteConv(long id) {
        getWritableDatabase().delete("msg", "conv=?", new String[]{String.valueOf(id)});
        getWritableDatabase().delete("conv", "_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteConvs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i));
        }
        String in = sb.toString();
        getWritableDatabase().delete("msg", "conv IN (" + in + ")", null);
        getWritableDatabase().delete("conv", "_id IN (" + in + ")", null);
    }

    // ── 消息 ────────────────────────────────────────────────────────

    public long addMsg(long conv, String role, String text) {
        ContentValues cv = new ContentValues();
        cv.put("conv", conv);
        cv.put("role", role);
        cv.put("text", text);
        cv.put("at", System.currentTimeMillis());
        cv.put("uuid", java.util.UUID.randomUUID().toString());
        long id = getWritableDatabase().insert("msg", null, cv);
        touchConv(conv, null);
        return id;
    }

    public void setAudio(long id, String path) {
        if (id <= 0 || path == null) return;
        ContentValues cv = new ContentValues();
        cv.put("audio", path);
        getWritableDatabase().update("msg", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    public void setTrans(long id, String trans) {
        if (id <= 0) return;
        ContentValues cv = new ContentValues();
        cv.put("trans", trans);
        getWritableDatabase().update("msg", cv, "_id=?", new String[]{String.valueOf(id)});
    }

    /** 单条消息当前的音频路径（重播前查一次，避免内存里的旧值还是 null） */
    public String msgAudio(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT audio FROM msg WHERE _id=?", new String[]{String.valueOf(id)})) {
            if (c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    public List<Msg> listMsgs(long conv) {
        List<Msg> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT _id,conv,role,text,trans,at,audio FROM msg WHERE conv=? ORDER BY _id ASC",
                new String[]{String.valueOf(conv)})) {
            while (c.moveToNext()) {
                Msg m = new Msg();
                m.id = c.getLong(0);
                m.conv = c.getLong(1);
                m.role = c.getString(2);
                m.text = c.getString(3);
                m.trans = c.getString(4);
                m.at = c.getLong(5);
                m.audio = c.getString(6);
                out.add(m);
            }
        }
        return out;
    }
}
