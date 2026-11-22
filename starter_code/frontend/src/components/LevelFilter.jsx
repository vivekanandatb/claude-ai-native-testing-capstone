import { LEVEL_FILTERS, formatLevel } from '../constants.js';

export default function LevelFilter({ selected, onSelect }) {
  return (
    <div className="level-filter" role="group" aria-label="Filter by level">
      <button
        type="button"
        className={selected === null ? 'chip chip--on' : 'chip'}
        onClick={() => onSelect(null)}
      >
        All levels
      </button>
      {LEVEL_FILTERS.map((level) => (
        <button
          key={level}
          type="button"
          className={selected === level ? 'chip chip--on' : 'chip'}
          onClick={() => onSelect(level)}
        >
          {formatLevel(level)}
        </button>
      ))}
    </div>
  );
}
