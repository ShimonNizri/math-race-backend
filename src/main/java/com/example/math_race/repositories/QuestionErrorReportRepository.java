package com.example.math_race.repositories;

import com.example.math_race.entities.QuestionErrorReportEntity;
import com.example.math_race.entities.QuestionErrorReportEntity.ReportStatus;
import com.example.math_race.entities.QuestionErrorReportEntity.ReporterType;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
@Repository
public class QuestionErrorReportRepository extends BaseRepository {

    @Autowired
    public QuestionErrorReportRepository(SessionFactory sf) {
        super(sf);
    }

    public List<QuestionErrorReportEntity> findReportsByFilters(
            String templateId,
            ReportStatus status,
            ReporterType type,
            String reporterId,
            int page,
            int size) {

        StringBuilder hql = new StringBuilder("FROM QuestionErrorReportEntity WHERE 1=1");

        if (templateId != null) {
            hql.append(" AND templateId = :templateId");
        }
        if (status != null) {
            hql.append(" AND status = :status");
        }
        if (type != null) {
            hql.append(" AND reporterType = :type");
        }
        if (reporterId != null) {
            hql.append(" AND reporterId = :reporterId");
        }

        hql.append(" ORDER BY updatedDate DESC");

        Query<QuestionErrorReportEntity> query = getCurrentSession().createQuery(hql.toString(), QuestionErrorReportEntity.class);

        if (templateId != null) {
            query.setParameter("templateId", templateId);
        }
        if (status != null) {
            query.setParameter("status", status);
        }
        if (type != null) {
            query.setParameter("type", type);
        }
        if (reporterId != null) {
            query.setParameter("reporterId", reporterId);
        }

        query.setFirstResult(page * size);
        query.setMaxResults(size);

        return query.list();
    }
}
