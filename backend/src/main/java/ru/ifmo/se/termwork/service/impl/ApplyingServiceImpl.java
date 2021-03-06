package ru.ifmo.se.termwork.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ifmo.se.termwork.domain.*;
import ru.ifmo.se.termwork.dto.ClaimDto;
import ru.ifmo.se.termwork.repository.OlympiadRepository;
import ru.ifmo.se.termwork.repository.SpecialityRepository;
import ru.ifmo.se.termwork.repository.StudentRepository;
import ru.ifmo.se.termwork.service.ApplyingService;
import ru.ifmo.se.termwork.service.ComputeService;
import ru.ifmo.se.termwork.support.exception.ApiException;
import ru.ifmo.se.termwork.support.exception.ClientException;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApplyingServiceImpl implements ApplyingService {

    private final StudentRepository studentRepository;

    private final SpecialityRepository specialityRepository;

    private final OlympiadRepository olympiadRepository;

    private final ComputeService computeService;

    @Override
    public void applyForSpeciality(int studentId, ClaimDto claimDto) {
        Student student = studentRepository.findWithAllById(studentId).
                orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "exception.user.notFound"));

        Speciality speciality = specialityRepository.findWithAllById(claimDto.getSpecialityId()).
                orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "exception.speciality.notFound",
                        claimDto.getSpecialityId()));

        Set<Rating> ratings = student.getRatings();
        if(ratings.size() >= 5)
            throw new ClientException(HttpStatus.BAD_REQUEST, "exception.applying.max");

        if(claimDto.isOriginals() &&
                ratings.stream().anyMatch(r -> r.isOriginals() && r.getSpeciality().getId() != speciality.getId()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "exception.applying.originals.used");

        Olympiad olympiad = null;
        if(claimDto.getOlympiadId() != -1 ){
            Olympiad selectedOlympiad = olympiadRepository.findById(claimDto.getOlympiadId()).
                    orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "exception.olympiad.notFound",
                            claimDto.getOlympiadId()));

            if(speciality.getRequirements().
                    stream().filter(r -> r.getId().getSubject().equals(selectedOlympiad.getSubject())).
                    noneMatch(r -> r.getMinLevelOfOlympiad() >= selectedOlympiad.getLevel()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "exception.applying.olympiad.invalid");

            if(ratings.stream().anyMatch(r -> r.getOlympiad().equals(selectedOlympiad)))
                throw new ApiException(HttpStatus.BAD_REQUEST, "exception.applying.olympiad.used");
            olympiad = selectedOlympiad;
        }

        for(Requirement req : speciality.getRequirements()){
            Subject reqSubject = req.getId().getSubject();
            Exam exam = student.getExams().stream().filter(e -> e.getId().getSubject().equals(reqSubject)).
                    findFirst().orElseThrow(() ->
                    new ClientException(HttpStatus.BAD_REQUEST, "exception.applying.exam.notFound", reqSubject.getName()));

            if(exam.getScore() < req.getMinScore())
                throw new ClientException(HttpStatus.BAD_REQUEST, "exception.applying.exam.invalid", reqSubject.getName());
        }

        Rating rating = student.applyFor(speciality, olympiad, claimDto.getPriority(), claimDto.isOriginals());
        if(olympiad == null)
            computeService.computeScoreAndSaveAsync(student, rating);
        else
            studentRepository.save(student);
    }

    @Override
    public void cancelApplication(int studentId, int specialityId) {
        Student student = studentRepository.findWithRatingsById(studentId).
                orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "exception.user.notFound"));

        Speciality speciality = specialityRepository.findById(specialityId).
                orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "exception.speciality.notFound",
                        specialityId));
        student.cancelApplication(speciality);
        studentRepository.save(student);
    }
}
