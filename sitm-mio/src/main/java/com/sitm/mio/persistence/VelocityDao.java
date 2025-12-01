package com.sitm.mio.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class VelocityDao {

    private static final String UPSERT_SQL = "INSERT INTO velocity_by_arc"
            + " (year_month, line_id, arc_id, avg_velocity, sample_count, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, now())"
            + " ON CONFLICT (year_month, line_id, arc_id) DO UPDATE"
            + " SET avg_velocity = EXCLUDED.avg_velocity, sample_count = EXCLUDED.sample_count, updated_at = now();";

    public void upsert(String yearMonth, String lineId, String arcId, double avgVelocity, long sampleCount) {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(UPSERT_SQL)) {

            ps.setString(1, yearMonth);
            ps.setString(2, lineId);
            ps.setString(3, arcId);
            ps.setDouble(4, avgVelocity);
            ps.setLong(5, sampleCount);

            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error persisting velocity_by_arc: " + e.getMessage());
        }
    }

    public String queryVelocities(String yearMonth, String lineId, String arcId) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        String sql = "SELECT year_month, line_id, arc_id, avg_velocity, sample_count, updated_at FROM velocity_by_arc WHERE 1=1";
        if (yearMonth != null && !yearMonth.isEmpty()) sql += " AND year_month = '" + yearMonth + "'";
        if (lineId != null && !lineId.isEmpty()) sql += " AND line_id = '" + lineId + "'";
        if (arcId != null && !arcId.isEmpty()) sql += " AND arc_id = '" + arcId + "'";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            java.sql.ResultSet rs = ps.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"year_month\":\"").append(rs.getString("year_month")).append("\"");
                sb.append(",\"line_id\":\"").append(rs.getString("line_id")).append("\"");
                sb.append(",\"arc_id\":\"").append(rs.getString("arc_id")).append("\"");
                sb.append(",\"avg_velocity\":").append(rs.getDouble("avg_velocity"));
                sb.append(",\"sample_count\":").append(rs.getLong("sample_count"));
                sb.append(",\"updated_at\":\"").append(rs.getTimestamp("updated_at")).append("\"}");
            }
        } catch (SQLException e) {
            System.err.println("Error querying velocity_by_arc: " + e.getMessage());
        }
        sb.append("]");
        return sb.toString();
    }

    public static String currentYearMonth() {
        ZonedDateTime now = ZonedDateTime.now();
        return now.format(DateTimeFormatter.ofPattern("yyyy_MM"));
    }
}
