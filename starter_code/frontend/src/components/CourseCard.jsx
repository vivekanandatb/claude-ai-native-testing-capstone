import { formatLevel } from '../constants.js';

export default function CourseCard({ course }) {
  return (
    <li className="course-card">
      <div className="course-card__head">
        <span className="course-card__code">{course.code}</span>
        <span className="course-card__level">{formatLevel(course.level)}</span>
      </div>
      <h2 className="course-card__title">{course.title}</h2>
      <p className="course-card__credits">{course.credits} credits</p>
    </li>
  );
}
