import { useEffect, useState } from 'react';
import CourseList from './components/CourseList.jsx';
import LevelFilter from './components/LevelFilter.jsx';
import { fetchCourses } from './api/catalog.js';

export default function App() {
  const [courses, setCourses] = useState([]);
  const [level, setLevel] = useState(null);
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setStatus('loading');

    fetchCourses({ level })
      .then((data) => {
        if (!cancelled) {
          setCourses(data);
          setStatus('ready');
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err.message);
          setStatus('error');
        }
      });

    return () => {
      cancelled = true;
    };
  }, [level]);

  return (
    <main className="page">
      <header className="page__head">
        <p className="eyebrow">Global Learning</p>
        <h1>Course catalogue</h1>
      </header>
      <LevelFilter selected={level} onSelect={setLevel} />
      <CourseList courses={courses} status={status} error={error} />
    </main>
  );
}
